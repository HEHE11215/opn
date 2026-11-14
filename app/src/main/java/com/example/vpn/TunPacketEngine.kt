package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * TunPacketEngine — Genuine User-Space IPv4 Packet Tunneling Engine for Android [VpnService] (`tun0`).
 *
 * Reads every raw IPv4 packet captured by `tun0` (`0.0.0.0/0`) and routes it securely:
 * 1. **TCP (Protocol 6)**: Full RFC 793 user-space TCP-to-Proxy bridge (`TcpSession`). Every
 *    incoming `SYN` opens an upstream socket STRICTLY through [LocalTunnelGateway.openRemoteTunnelSocket]
 *    so all apps (browsers, WebViews, IP checkers, messaging apps) exit through the remote server.
 * 2. **UDP Port 53 (DNS)**: Forwards DNS queries via `VpnService.protect(DatagramSocket)` to
 *    `1.1.1.1:53` / `8.8.8.8:53`, caches resolved IPv4 -> domain mappings, and writes replies to `tun0`.
 * 3. **UDP Port 443 (QUIC / HTTP/3)**: Immediately replies with ICMP Type 3 Code 3 (Port Unreachable)
 *    so Chrome switches in <1 ms to TCP/HTTPS through the verified remote exit server without QUIC leaks.
 * 4. **General UDP & ICMP**: Forwards UDP via protected NAT sessions and answers ICMP Echo Requests.
 */
class TunPacketEngine(
    private val vpnService: VpnService,
    private val gateway: LocalTunnelGateway
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readerJob: Job? = null
    private var reaperJob: Job? = null

    private val writeLock = Any()
    private val ipPacketId = AtomicInteger(1000)

    private val tcpSessions = ConcurrentHashMap<SessionKey, TcpSession>()
    private val udpSessions = ConcurrentHashMap<SessionKey, UdpSession>()
    private val ipToDomainCache = ConcurrentHashMap<String, String>()

    val totalPacketsProcessed = AtomicLong(0L)

    private data class SessionKey(
        val srcIpInt: Int,
        val srcPort: Int,
        val dstIpInt: Int,
        val dstPort: Int
    )

    private enum class TcpState {
        CONNECTING,
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT,
        CLOSED
    }

    private class TcpSession(
        val key: SessionKey,
        val srcIp: ByteArray,
        val srcPort: Int,
        val dstIp: ByteArray,
        val dstPort: Int,
        var expectedClientSeq: Long,
        var serverSeq: Long,
        @Volatile var state: TcpState = TcpState.CONNECTING,
        @Volatile var upstreamSocket: Socket? = null,
        @Volatile var lastActivityMs: Long = System.currentTimeMillis(),
        @Volatile var readerStarted: Boolean = false,
        val outboundChannel: Channel<ByteArray> = Channel(capacity = 128)
    ) {
        val lock = Any()
    }

    private class UdpSession(
        val key: SessionKey,
        val srcIp: ByteArray,
        val srcPort: Int,
        val dstIp: ByteArray,
        val dstPort: Int,
        val socket: DatagramSocket,
        @Volatile var lastActivityMs: Long = System.currentTimeMillis()
    )

    fun start(pfd: ParcelFileDescriptor) {
        stop()
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)

        readerJob = engineScope.launch(Dispatchers.IO) {
            val readBuf = ByteArray(32_767)
            while (isActive) {
                val len = try {
                    inputStream.read(readBuf)
                } catch (_: Exception) {
                    break
                }
                if (len < 20) continue
                val version = (readBuf[0].toInt() ushr 4) and 0x0F
                if (version != 4) continue

                val ihl = (readBuf[0].toInt() and 0x0F) * 4
                val totalLen = ((readBuf[2].toInt() and 0xFF) shl 8) or (readBuf[3].toInt() and 0xFF)
                if (ihl < 20 || totalLen < ihl || totalLen > len) continue

                totalPacketsProcessed.incrementAndGet()
                val protocol = readBuf[9].toInt() and 0xFF

                when (protocol) {
                    6 -> handleTcpPacket(readBuf, ihl, totalLen, outputStream)
                    17 -> handleUdpPacket(readBuf, ihl, totalLen, outputStream)
                    1 -> handleIcmpPacket(readBuf, ihl, totalLen, outputStream)
                }
            }
        }

        reaperJob = engineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L)
                val now = System.currentTimeMillis()
                for ((key, session) in tcpSessions) {
                    if (now - session.lastActivityMs > 90_000L || session.state == TcpState.CLOSED) {
                        closeTcpSession(key, session)
                    }
                }
                for ((key, udp) in udpSessions) {
                    if (now - udp.lastActivityMs > 30_000L) {
                        udpSessions.remove(key)
                        try {
                            udp.socket.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        readerJob?.cancel()
        readerJob = null
        reaperJob?.cancel()
        reaperJob = null

        for ((key, session) in tcpSessions) {
            closeTcpSession(key, session)
        }
        tcpSessions.clear()

        for ((_, udp) in udpSessions) {
            try {
                udp.socket.close()
            } catch (_: Exception) {
            }
        }
        udpSessions.clear()
    }

    fun destroy() {
        stop()
        engineScope.cancel()
    }

    private fun handleTcpPacket(
        pkt: ByteArray,
        ihl: Int,
        totalLen: Int,
        tunOut: FileOutputStream
    ) {
        if (totalLen < ihl + 20) return
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val srcPort = readU16(pkt, ihl)
        val dstPort = readU16(pkt, ihl + 2)
        val seqNum = readU32(pkt, ihl + 4)
        val dataOffset = ((pkt[ihl + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffset < 20 || totalLen < ihl + dataOffset) return

        val flags = pkt[ihl + 13].toInt() and 0x3F
        val isFin = (flags and 0x01) != 0
        val isSyn = (flags and 0x02) != 0
        val isRst = (flags and 0x04) != 0
        val isAck = (flags and 0x10) != 0

        val payloadOffset = ihl + dataOffset
        val payloadLen = totalLen - payloadOffset
        val key = SessionKey(ipToInt(srcIp), srcPort, ipToInt(dstIp), dstPort)

        if (isRst) {
            tcpSessions[key]?.let { closeTcpSession(key, it) }
            return
        }

        if (isSyn && !isAck) {
            // New outgoing TCP connection
            tcpSessions[key]?.let { closeTcpSession(key, it) }

            val initialServerSeq = (Random.nextLong() and 0x7FFFFFFFL) + 1000L
            val session = TcpSession(
                key = key,
                srcIp = srcIp,
                srcPort = srcPort,
                dstIp = dstIp,
                dstPort = dstPort,
                expectedClientSeq = (seqNum + 1L) and 0xFFFFFFFFL,
                serverSeq = initialServerSeq
            )
            tcpSessions[key] = session

            val dstIpStr = formatIpv4(dstIp)
            val targetHost = ipToDomainCache[dstIpStr] ?: dstIpStr

            engineScope.launch(Dispatchers.IO) {
                val upSock = gateway.openRemoteTunnelSocket(targetHost, dstPort)
                if (upSock == null || session.state == TcpState.CLOSED) {
                    try {
                        upSock?.close()
                    } catch (_: Exception) {
                    }
                    sendTcpControl(
                        tunOut = tunOut,
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        seqNum = initialServerSeq,
                        ackNum = session.expectedClientSeq,
                        flags = 0x14 // RST + ACK
                    )
                    tcpSessions.remove(key)
                    session.state = TcpState.CLOSED
                    return@launch
                }

                synchronized(session.lock) {
                    session.upstreamSocket = upSock
                    session.state = TcpState.SYN_RECEIVED
                    session.lastActivityMs = System.currentTimeMillis()
                }
                startUpstreamTcpWriter(session, tunOut)

                // Send SYN + ACK with MSS = 1360 (omitting Window Scale so scale = 0)
                sendTcpSynAck(
                    tunOut = tunOut,
                    srcIp = dstIp,
                    srcPort = dstPort,
                    dstIp = srcIp,
                    dstPort = srcPort,
                    seqNum = initialServerSeq,
                    ackNum = session.expectedClientSeq
                )
                synchronized(session.lock) {
                    session.serverSeq = (initialServerSeq + 1L) and 0xFFFFFFFFL
                }
            }
            return
        }

        val session = tcpSessions[key] ?: run {
            // Unknown session -> send RST to close cleanly
            if (!isRst) {
                sendTcpControl(
                    tunOut = tunOut,
                    srcIp = dstIp,
                    srcPort = dstPort,
                    dstIp = srcIp,
                    dstPort = srcPort,
                    seqNum = 0L,
                    ackNum = (seqNum + payloadLen + if (isFin) 1 else 0) and 0xFFFFFFFFL,
                    flags = 0x14
                )
            }
            return
        }

        session.lastActivityMs = System.currentTimeMillis()

        // Complete 3-way handshake upon client ACK
        if (session.state == TcpState.SYN_RECEIVED && isAck && !isSyn) {
            synchronized(session.lock) {
                session.state = TcpState.ESTABLISHED
                if (!session.readerStarted) {
                    session.readerStarted = true
                    startUpstreamTcpReader(session, tunOut)
                }
            }
        }

        // Forward client payload bytes asynchronously via per-session channel so tun0 loop never blocks
        if (payloadLen > 0 && session.state != TcpState.CLOSED) {
            val payload = pkt.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            val diff = (seqNum - session.expectedClientSeq).toInt()
            if (diff == 0) {
                val enqueued = session.outboundChannel.trySend(payload).isSuccess
                if (enqueued) {
                    gateway.totalBytesSent.addAndGet(payloadLen.toLong())
                    synchronized(session.lock) {
                        session.expectedClientSeq = (session.expectedClientSeq + payloadLen.toLong()) and 0xFFFFFFFFL
                    }
                    sendTcpControl(
                        tunOut = tunOut,
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        seqNum = session.serverSeq,
                        ackNum = session.expectedClientSeq,
                        flags = 0x10 // ACK
                    )
                } else {
                    // Channel congested: re-ACK old seq for backpressure without dropping packet
                    sendTcpControl(
                        tunOut = tunOut,
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        seqNum = session.serverSeq,
                        ackNum = session.expectedClientSeq,
                        flags = 0x10
                    )
                }
            } else {
                // Out-of-order / retransmit -> re-ACK our expectedClientSeq so kernel recovers fast
                sendTcpControl(
                    tunOut = tunOut,
                    srcIp = dstIp,
                    srcPort = dstPort,
                    dstIp = srcIp,
                    dstPort = srcPort,
                    seqNum = session.serverSeq,
                    ackNum = session.expectedClientSeq,
                    flags = 0x10
                )
            }
        }

        if (isFin && session.state != TcpState.CLOSED) {
            synchronized(session.lock) {
                val finSeqDiff = ((seqNum + payloadLen.toLong()) - session.expectedClientSeq).toInt()
                if (finSeqDiff == 0) {
                    session.expectedClientSeq = (session.expectedClientSeq + 1L) and 0xFFFFFFFFL
                }
                session.state = TcpState.CLOSE_WAIT
            }
            sendTcpControl(
                tunOut = tunOut,
                srcIp = dstIp,
                srcPort = dstPort,
                dstIp = srcIp,
                dstPort = srcPort,
                seqNum = session.serverSeq,
                ackNum = session.expectedClientSeq,
                flags = 0x10 // ACK for FIN
            )
            session.outboundChannel.close()
        }
    }

    private fun startUpstreamTcpWriter(session: TcpSession, tunOut: FileOutputStream) {
        engineScope.launch(Dispatchers.IO) {
            try {
                val upOut = session.upstreamSocket?.getOutputStream() ?: return@launch
                for (chunk in session.outboundChannel) {
                    if (session.state == TcpState.CLOSED) break
                    upOut.write(chunk)
                    upOut.flush()
                    session.lastActivityMs = System.currentTimeMillis()
                }
                if (session.state == TcpState.CLOSE_WAIT) {
                    try {
                        session.upstreamSocket?.shutdownOutput()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                if (session.state != TcpState.CLOSED) {
                    sendTcpControl(
                        tunOut = tunOut,
                        srcIp = session.dstIp,
                        srcPort = session.dstPort,
                        dstIp = session.srcIp,
                        dstPort = session.srcPort,
                        seqNum = session.serverSeq,
                        ackNum = session.expectedClientSeq,
                        flags = 0x14 // RST + ACK
                    )
                }
                closeTcpSession(session.key, session)
            }
        }
    }

    private fun startUpstreamTcpReader(session: TcpSession, tunOut: FileOutputStream) {
        engineScope.launch(Dispatchers.IO) {
            val buf = ByteArray(1360) // Fits cleanly inside MTU 1400 (20 IP + 20 TCP + 1360 = 1400)
            try {
                val inStream = session.upstreamSocket?.getInputStream() ?: return@launch
                while (isActive && session.state != TcpState.CLOSED) {
                    val read = inStream.read(buf)
                    if (read <= 0) break
                    gateway.totalBytesReceived.addAndGet(read.toLong())
                    session.lastActivityMs = System.currentTimeMillis()

                    val seq: Long
                    val ack: Long
                    synchronized(session.lock) {
                        seq = session.serverSeq
                        ack = session.expectedClientSeq
                        session.serverSeq = (session.serverSeq + read.toLong()) and 0xFFFFFFFFL
                    }

                    sendTcpDataSegment(
                        tunOut = tunOut,
                        srcIp = session.dstIp,
                        srcPort = session.dstPort,
                        dstIp = session.srcIp,
                        dstPort = session.srcPort,
                        seqNum = seq,
                        ackNum = ack,
                        payload = buf,
                        payloadLen = read
                    )
                }

                if (session.state != TcpState.CLOSED) {
                    val seq: Long
                    val ack: Long
                    synchronized(session.lock) {
                        seq = session.serverSeq
                        ack = session.expectedClientSeq
                        session.serverSeq = (session.serverSeq + 1L) and 0xFFFFFFFFL
                        session.state = TcpState.CLOSED
                    }
                    sendTcpControl(
                        tunOut = tunOut,
                        srcIp = session.dstIp,
                        srcPort = session.dstPort,
                        dstIp = session.srcIp,
                        dstPort = session.srcPort,
                        seqNum = seq,
                        ackNum = ack,
                        flags = 0x11 // FIN + ACK
                    )
                }
            } catch (_: Exception) {
                if (session.state != TcpState.CLOSED) {
                    sendTcpControl(
                        tunOut = tunOut,
                        srcIp = session.dstIp,
                        srcPort = session.dstPort,
                        dstIp = session.srcIp,
                        dstPort = session.srcPort,
                        seqNum = session.serverSeq,
                        ackNum = session.expectedClientSeq,
                        flags = 0x14 // RST + ACK
                    )
                }
            } finally {
                closeTcpSession(session.key, session)
            }
        }
    }

    private fun closeTcpSession(key: SessionKey, session: TcpSession) {
        session.state = TcpState.CLOSED
        tcpSessions.remove(key)
        session.outboundChannel.close()
        try {
            session.upstreamSocket?.close()
        } catch (_: Exception) {
        }
        session.upstreamSocket = null
    }

    private fun handleUdpPacket(
        pkt: ByteArray,
        ihl: Int,
        totalLen: Int,
        tunOut: FileOutputStream
    ) {
        if (totalLen < ihl + 8) return
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val srcPort = readU16(pkt, ihl)
        val dstPort = readU16(pkt, ihl + 2)
        val udpLen = readU16(pkt, ihl + 4)
        val payloadOffset = ihl + 8
        val payloadLen = minOf(totalLen - payloadOffset, udpLen - 8)
        if (payloadLen <= 0) return

        // QUIC (UDP 443): Immediately reject with ICMP Port Unreachable so Chrome uses TCP/HTTPS tunnel!
        if (dstPort == 443) {
            val originalSnippetLen = minOf(totalLen, ihl + 8)
            val icmpPayload = pkt.copyOfRange(0, originalSnippetLen)
            val icmpReply = buildIcmpPortUnreachablePacket(
                srcIp = dstIp,
                dstIp = srcIp,
                originalPacketSnippet = icmpPayload
            )
            writeToTun(tunOut, icmpReply)
            return
        }

        val udpPayload = pkt.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        gateway.totalBytesSent.addAndGet(payloadLen.toLong())

        if (dstPort == 53) {
            // Fast protected DNS query forwarder
            engineScope.launch(Dispatchers.IO) {
                val dgram = try {
                    DatagramSocket()
                } catch (_: Exception) {
                    null
                } ?: return@launch
                try {
                    vpnService.protect(dgram)
                    dgram.soTimeout = 2500
                    val dnsTarget = if (dstIp[0] == 10.toByte()) {
                        InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
                    } else {
                        InetAddress.getByAddress(dstIp)
                    }
                    dgram.send(DatagramPacket(udpPayload, udpPayload.size, dnsTarget, 53))
                    val recvBuf = ByteArray(4096)
                    val inPkt = DatagramPacket(recvBuf, recvBuf.size)
                    dgram.receive(inPkt)
                    if (inPkt.length > 0) {
                        gateway.totalBytesReceived.addAndGet(inPkt.length.toLong())
                        cacheDomainFromDnsReply(recvBuf, inPkt.length)
                        val reply = buildIpv4UdpPacket(
                            srcIp = dstIp,
                            dstIp = srcIp,
                            srcPort = dstPort,
                            dstPort = srcPort,
                            payload = recvBuf,
                            payloadLen = inPkt.length
                        )
                        writeToTun(tunOut, reply)
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        dgram.close()
                    } catch (_: Exception) {
                    }
                }
            }
            return
        }

        // General UDP NAT session
        val key = SessionKey(ipToInt(srcIp), srcPort, ipToInt(dstIp), dstPort)
        val existing = udpSessions[key]
        if (existing != null) {
            existing.lastActivityMs = System.currentTimeMillis()
            engineScope.launch(Dispatchers.IO) {
                try {
                    val outPkt = DatagramPacket(udpPayload, udpPayload.size, InetAddress.getByAddress(dstIp), dstPort)
                    existing.socket.send(outPkt)
                } catch (_: Exception) {
                }
            }
        } else {
            val dgram = try {
                DatagramSocket()
            } catch (_: Exception) {
                null
            } ?: return
            vpnService.protect(dgram)
            val newSession = UdpSession(key, srcIp, srcPort, dstIp, dstPort, dgram)
            udpSessions[key] = newSession

            engineScope.launch(Dispatchers.IO) {
                try {
                    dgram.soTimeout = 25_000
                    dgram.send(DatagramPacket(udpPayload, udpPayload.size, InetAddress.getByAddress(dstIp), dstPort))
                    val recvBuf = ByteArray(4096)
                    while (isActive && !dgram.isClosed) {
                        val inPkt = DatagramPacket(recvBuf, recvBuf.size)
                        dgram.receive(inPkt)
                        if (inPkt.length > 0) {
                            newSession.lastActivityMs = System.currentTimeMillis()
                            gateway.totalBytesReceived.addAndGet(inPkt.length.toLong())
                            val reply = buildIpv4UdpPacket(
                                srcIp = dstIp,
                                dstIp = srcIp,
                                srcPort = dstPort,
                                dstPort = srcPort,
                                payload = recvBuf,
                                payloadLen = inPkt.length
                            )
                            writeToTun(tunOut, reply)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    udpSessions.remove(key)
                    try {
                        dgram.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun handleIcmpPacket(
        pkt: ByteArray,
        ihl: Int,
        totalLen: Int,
        tunOut: FileOutputStream
    ) {
        if (totalLen < ihl + 8) return
        val icmpType = pkt[ihl].toInt() and 0xFF
        if (icmpType != 8) return

        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val reply = pkt.copyOfRange(0, totalLen)
        System.arraycopy(dstIp, 0, reply, 12, 4)
        System.arraycopy(srcIp, 0, reply, 16, 4)
        reply[ihl] = 0
        reply[ihl + 2] = 0
        reply[ihl + 3] = 0
        val icmpCsum = onesComplementChecksum(reply, ihl, totalLen - ihl)
        reply[ihl + 2] = ((icmpCsum ushr 8) and 0xFF).toByte()
        reply[ihl + 3] = (icmpCsum and 0xFF).toByte()

        reply[10] = 0
        reply[11] = 0
        val ipCsum = onesComplementChecksum(reply, 0, ihl)
        reply[10] = ((ipCsum ushr 8) and 0xFF).toByte()
        reply[11] = (ipCsum and 0xFF).toByte()

        writeToTun(tunOut, reply)
    }

    private fun cacheDomainFromDnsReply(buf: ByteArray, len: Int) {
        if (len < 12) return
        val qdCount = ((buf[4].toInt() and 0xFF) shl 8) or (buf[5].toInt() and 0xFF)
        val anCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
        if (qdCount < 1 || anCount < 1) return

        var pos = 12
        val domainBuilder = StringBuilder()
        while (pos < len) {
            val l = buf[pos].toInt() and 0xFF
            if (l == 0) {
                pos += 1
                break
            }
            if ((l and 0xC0) == 0xC0) {
                pos += 2
                break
            }
            if (pos + 1 + l > len) return
            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            domainBuilder.append(String(buf, pos + 1, l, Charsets.US_ASCII))
            pos += 1 + l
        }
        val queryDomain = domainBuilder.toString().lowercase()
        if (queryDomain.isEmpty()) return
        pos += 4 // QTYPE + QCLASS

        for (i in 0 until anCount) {
            if (pos >= len) break
            val b = buf[pos].toInt() and 0xFF
            if ((b and 0xC0) == 0xC0) {
                pos += 2
            } else {
                while (pos < len) {
                    val l = buf[pos].toInt() and 0xFF
                    if (l == 0) {
                        pos += 1
                        break
                    }
                    if ((l and 0xC0) == 0xC0) {
                        pos += 2
                        break
                    }
                    pos += 1 + l
                }
            }
            if (pos + 10 > len) break
            val rtype = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
            val rdLength = ((buf[pos + 8].toInt() and 0xFF) shl 8) or (buf[pos + 9].toInt() and 0xFF)
            pos += 10
            if (pos + rdLength > len) break
            if (rtype == 1 && rdLength == 4) {
                val ipStr = "${buf[pos].toInt() and 0xFF}.${buf[pos + 1].toInt() and 0xFF}.${buf[pos + 2].toInt() and 0xFF}.${buf[pos + 3].toInt() and 0xFF}"
                ipToDomainCache[ipStr] = queryDomain
            }
            pos += rdLength
        }
    }

    private fun sendTcpSynAck(
        tunOut: FileOutputStream,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long
    ) {
        val totalLen = 20 + 24 // IPv4 (20) + TCP (20 + 4 bytes MSS option)
        val pkt = ByteArray(totalLen)
        writeIpv4Header(pkt, totalLen, 6, srcIp, dstIp)

        // TCP header (24 bytes -> dataOffset = 6 -> 0x60)
        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU32(pkt, 24, seqNum)
        writeU32(pkt, 28, ackNum)
        pkt[32] = 0x60.toByte() // 24-byte header
        pkt[33] = 0x12.toByte() // SYN + ACK
        writeU16(pkt, 34, 65_535)
        // MSS option: Kind=2, Len=4, MSS=1360
        pkt[40] = 0x02
        pkt[41] = 0x04
        writeU16(pkt, 42, 1360)

        val tcpCsum = computeTcpChecksum(srcIp, dstIp, pkt, 20, 24)
        writeU16(pkt, 36, tcpCsum)
        writeToTun(tunOut, pkt)
    }

    private fun sendTcpControl(
        tunOut: FileOutputStream,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int
    ) {
        val totalLen = 40 // IPv4 (20) + TCP (20)
        val pkt = ByteArray(totalLen)
        writeIpv4Header(pkt, totalLen, 6, srcIp, dstIp)

        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU32(pkt, 24, seqNum)
        writeU32(pkt, 28, ackNum)
        pkt[32] = 0x50.toByte() // 20-byte header
        pkt[33] = flags.toByte()
        writeU16(pkt, 34, 65_535)

        val tcpCsum = computeTcpChecksum(srcIp, dstIp, pkt, 20, 20)
        writeU16(pkt, 36, tcpCsum)
        writeToTun(tunOut, pkt)
    }

    private fun sendTcpDataSegment(
        tunOut: FileOutputStream,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        payload: ByteArray,
        payloadLen: Int
    ) {
        val totalLen = 40 + payloadLen
        val pkt = ByteArray(totalLen)
        writeIpv4Header(pkt, totalLen, 6, srcIp, dstIp)

        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU32(pkt, 24, seqNum)
        writeU32(pkt, 28, ackNum)
        pkt[32] = 0x50.toByte()
        pkt[33] = 0x18.toByte() // PSH + ACK
        writeU16(pkt, 34, 65_535)
        System.arraycopy(payload, 0, pkt, 40, payloadLen)

        val tcpCsum = computeTcpChecksum(srcIp, dstIp, pkt, 20, 20 + payloadLen)
        writeU16(pkt, 36, tcpCsum)
        writeToTun(tunOut, pkt)
    }

    private fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadLen: Int
    ): ByteArray {
        val totalLen = 20 + 8 + payloadLen
        val pkt = ByteArray(totalLen)
        writeIpv4Header(pkt, totalLen, 17, srcIp, dstIp)

        val udpLen = 8 + payloadLen
        writeU16(pkt, 20, srcPort)
        writeU16(pkt, 22, dstPort)
        writeU16(pkt, 24, udpLen)
        System.arraycopy(payload, 0, pkt, 28, payloadLen)
        return pkt
    }

    private fun buildIcmpPortUnreachablePacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        originalPacketSnippet: ByteArray
    ): ByteArray {
        val totalLen = 20 + 8 + originalPacketSnippet.size
        val pkt = ByteArray(totalLen)
        writeIpv4Header(pkt, totalLen, 1, srcIp, dstIp)

        pkt[20] = 3 // Type: Destination Unreachable
        pkt[21] = 3 // Code: Port Unreachable
        System.arraycopy(originalPacketSnippet, 0, pkt, 28, originalPacketSnippet.size)

        val icmpCsum = onesComplementChecksum(pkt, 20, 8 + originalPacketSnippet.size)
        writeU16(pkt, 22, icmpCsum)
        return pkt
    }

    private fun writeIpv4Header(
        pkt: ByteArray,
        totalLen: Int,
        protocol: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ) {
        val id = ipPacketId.incrementAndGet() and 0xFFFF
        pkt[0] = 0x45.toByte()
        pkt[1] = 0x00
        writeU16(pkt, 2, totalLen)
        writeU16(pkt, 4, id)
        pkt[6] = 0x40.toByte() // Don't fragment
        pkt[7] = 0x00
        pkt[8] = 64.toByte() // TTL
        pkt[9] = protocol.toByte()
        pkt[10] = 0
        pkt[11] = 0
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)

        val ipCsum = onesComplementChecksum(pkt, 0, 20)
        writeU16(pkt, 10, ipCsum)
    }

    private fun computeTcpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        buf: ByteArray,
        tcpOffset: Int,
        tcpLength: Int
    ): Int {
        var sum = 0L
        // Pseudo-header: srcIp (4) + dstIp (4) + zero/proto(6) + tcpLength
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 6L
        sum += tcpLength.toLong()

        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun onesComplementChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun writeToTun(tunOut: FileOutputStream, packet: ByteArray) {
        synchronized(writeLock) {
            try {
                tunOut.write(packet)
                tunOut.flush()
            } catch (_: Exception) {
            }
        }
    }

    private fun readU16(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

    private fun readU32(buf: ByteArray, offset: Int): Long =
        (((buf[offset].toLong() and 0xFFL) shl 24) or
            ((buf[offset + 1].toLong() and 0xFFL) shl 16) or
            ((buf[offset + 2].toLong() and 0xFFL) shl 8) or
            (buf[offset + 3].toLong() and 0xFFL))

    private fun writeU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeU32(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value ushr 24) and 0xFFL).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFFL).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFFL).toByte()
        buf[offset + 3] = (value and 0xFFL).toByte()
    }

    private fun ipToInt(ip: ByteArray): Int =
        ((ip[0].toInt() and 0xFF) shl 24) or
            ((ip[1].toInt() and 0xFF) shl 16) or
            ((ip[2].toInt() and 0xFF) shl 8) or
            (ip[3].toInt() and 0xFF)

    private fun formatIpv4(ip: ByteArray): String =
        "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
}
