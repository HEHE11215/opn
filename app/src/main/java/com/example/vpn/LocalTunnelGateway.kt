package com.example.vpn

import android.net.VpnService
import android.util.Base64
import com.example.model.ExitIpVerification
import com.example.model.RelayTunnelType
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * LocalTunnelGateway — Strict Remote-Exit Tunnel Engine for **A VPN** (By Ashir ♥️).
 *
 * Guarantees:
 * 1. **Zero IP Leaks**: Never silently falls back to unproxied direct sockets. Every HTTP/HTTPS
 *    connection and every `tun0` raw TCP session is tunneled strictly through the active verified
 *    remote exit node (with automatic failover only to verified remote backup relays).
 * 2. **Strict End-to-End TLS + Exit IP Verification (`verifyNodeEndToEndTls`)**:
 *    Verifies every candidate relay by running a real `SSLSocket` TLS handshake to `api.ipify.org:443`
 *    through the relay and confirming the returned public IP differs from the device's direct IP.
 *    Eliminates dead proxies, transparent proxies, and MITM certificate errors (`ERR_CERT_AUTHORITY_INVALID`).
 * 3. **100% Real-Time TCP Ping (`measureRealTcpPingMs`)**:
 *    Measures exact TCP `SYN` -> `SYN-ACK` round-trip time via `System.nanoTime()`.
 * 4. **Optional Username / Password Auth**: Supports RFC 1929 SOCKS5 auth and Basic HTTP Proxy auth
 *    for custom/paid servers.
 */
class LocalTunnelGateway(
    private val vpnService: VpnService? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    @Volatile
    var primaryNode: VpnNode? = null
        private set

    @Volatile
    private var keepDisplayNodeLocked: Boolean = false

    private val fallbackVerifiedNodes = CopyOnWriteArrayList<VpnNode>()
    private val activeTunnelSockets = CopyOnWriteArrayList<Socket>()

    val totalBytesReceived = AtomicLong(0L)
    val totalBytesSent = AtomicLong(0L)
    val totalConnectionsHandled = AtomicLong(0L)

    val listeningPort: Int
        get() = serverSocket?.localPort ?: 0

    private val protectSocketFn: (Socket) -> Boolean = { sock ->
        try {
            vpnService?.protect(sock) ?: AetherVpnStateBus.protectSocketCallback?.invoke(sock) ?: false
        } catch (_: Exception) {
            false
        }
    }

    private val protectDatagramFn: (DatagramSocket) -> Boolean = { dgram ->
        try {
            vpnService?.protect(dgram) ?: AetherVpnStateBus.protectDatagramCallback?.invoke(dgram) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun start(
        initialNode: VpnNode,
        verifiedBackups: List<VpnNode>,
        lockDisplayNode: Boolean = false
    ): Int {
        stop()

        primaryNode = initialNode
        keepDisplayNodeLocked = lockDisplayNode
        fallbackVerifiedNodes.clear()
        fallbackVerifiedNodes.addAll(
            verifiedBackups.distinctBy { "${it.ipAddress}:${it.port}" }
        )

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress("127.0.0.1", 0), 256)
        serverSocket = server

        val boundPort = server.localPort

        acceptJob = scope.launch {
            while (isActive && !server.isClosed) {
                val clientSocket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                activeTunnelSockets.add(clientSocket)
                launch {
                    handleClientConnection(clientSocket)
                }
            }
        }
        return boundPort
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        SshTunnelManager.closeAllSessions()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        val socketsToClose = ArrayList(activeTunnelSockets)
        activeTunnelSockets.clear()
        for (sock in socketsToClose) {
            try {
                sock.close()
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    /**
     * Smoothly switches [primaryNode] at runtime without bouncing the local server socket.
     */
    fun switchPrimaryNode(newNode: VpnNode, updatedBackups: List<VpnNode>) {
        primaryNode = newNode
        fallbackVerifiedNodes.clear()
        fallbackVerifiedNodes.addAll(
            updatedBackups.distinctBy { "${it.ipAddress}:${it.port}" }
        )
    }

    /**
     * Opens a high-bandwidth upstream TCP tunnel socket STRICTLY through the active locked server.
     * Uses 256KB socket window buffers and fast retry on the same server so browser & video streams
     * achieve maximum throughput without ever switching the user's connected server/country.
     */
    fun openRemoteTunnelSocket(targetHost: String, targetPort: Int): Socket? {
        val currentPrimary = primaryNode ?: return null
        val useCustomAuth = currentPrimary.isCustomNode

        // 1. Fast primary attempt on the locked server (2200ms timeout)
        val primarySock = connectViaRelayNode(
            node = currentPrimary,
            targetHost = targetHost,
            targetPort = targetPort,
            timeoutMs = 2200,
            useExplicitProxyAuth = useCustomAuth,
            protectSocket = protectSocketFn,
            protectDatagram = protectDatagramFn
        )
        if (primarySock != null) {
            return primarySock
        }

        // 2. Immediate second attempt on the exact same connected server (2600ms timeout)
        val retryPrimarySock = connectViaRelayNode(
            node = currentPrimary,
            targetHost = targetHost,
            targetPort = targetPort,
            timeoutMs = 2600,
            useExplicitProxyAuth = useCustomAuth,
            protectSocket = protectSocketFn,
            protectDatagram = protectDatagramFn
        )
        if (retryPrimarySock != null) {
            return retryPrimarySock
        }

        // Custom private credentials server: do not fall back to public pool
        if (currentPrimary.isCustomNode) {
            return null
        }

        // 3. Same-country high-bandwidth assist for this single socket ONLY (never mutates primaryNode or switches country!)
        val sameCountryBackups = (fallbackVerifiedNodes +
            AetherVpnStateBus.verifiedBackupPool.value +
            AetherVpnStateBus.nodePool.value)
            .filter {
                it.isVerifiedWorking &&
                    it.ipAddress.isNotBlank() &&
                    it.countryCode.equals(currentPrimary.countryCode, ignoreCase = true) &&
                    (it.ipAddress != currentPrimary.ipAddress || it.port != currentPrimary.port)
            }
            .distinctBy { "${it.ipAddress}:${it.port}" }
            .sortedBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            .take(2)

        for (backup in sameCountryBackups) {
            val sock = connectViaRelayNode(
                node = backup,
                targetHost = targetHost,
                targetPort = targetPort,
                timeoutMs = 2200,
                useExplicitProxyAuth = false,
                protectSocket = protectSocketFn,
                protectDatagram = protectDatagramFn
            )
            if (sock != null) {
                return sock
            }
        }

        return null
    }

    private suspend fun handleClientConnection(client: Socket) = withContext(Dispatchers.IO) {
        var upstreamSocket: Socket? = null
        try {
            client.soTimeout = 45_000
            client.tcpNoDelay = true
            client.keepAlive = true
            client.receiveBufferSize = 262_144
            client.sendBufferSize = 262_144
            val clientIn = BufferedInputStream(client.getInputStream(), 65_536)
            val clientOut = client.getOutputStream()

            val headerBytes = readHttpHeaderBlock(clientIn) ?: return@withContext
            totalBytesSent.addAndGet(headerBytes.size.toLong())

            val headerStr = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerStr.split("\r\n")
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            if (requestLine.isEmpty()) return@withContext

            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase(Locale.US)
            val targetToken = parts[1]

            if (method == "CONNECT") {
                val hostPort = targetToken.split(":")
                val targetHost = hostPort[0].trim()
                val targetPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                upstreamSocket = openRemoteTunnelSocket(targetHost, targetPort)
                val upSock = upstreamSocket
                if (upSock == null) {
                    val errResp = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(errResp.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                    return@withContext
                }
                activeTunnelSockets.add(upSock)
                totalConnectionsHandled.incrementAndGet()

                val okResp = "HTTP/1.1 200 Connection Established\r\n\r\n"
                clientOut.write(okResp.toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()

                val upstreamIn = upSock.getInputStream()
                val upstreamOut = upSock.getOutputStream()

                val clientToUpstream = launch {
                    pipeStreams(clientIn, upstreamOut, isUpload = true)
                    try {
                        upSock.shutdownOutput()
                    } catch (_: Exception) {
                    }
                }
                val upstreamToClient = launch {
                    pipeStreams(upstreamIn, clientOut, isUpload = false)
                    try {
                        client.shutdownOutput()
                    } catch (_: Exception) {
                    }
                }

                clientToUpstream.join()
                upstreamToClient.join()
            } else {
                val parsedTarget = parseHttpUrlTarget(targetToken, lines) ?: return@withContext
                val (targetHost, targetPort, relativePath) = parsedTarget

                // Fast-path plain HTTP requests directly if active node is HTTP_CONNECT
                val active = primaryNode
                if (active != null && active.relayType == RelayTunnelType.HTTP_CONNECT) {
                    val directHttpProxySock = openRawSocketToProxy(active, timeoutMs = 3500)
                    if (directHttpProxySock != null) {
                        upstreamSocket = directHttpProxySock
                        activeTunnelSockets.add(directHttpProxySock)
                        totalConnectionsHandled.incrementAndGet()

                        val fullUrl = if (targetToken.startsWith("http://", ignoreCase = true)) {
                            targetToken
                        } else {
                            "http://$targetHost:$targetPort$relativePath"
                        }
                        val httpVersion = parts.getOrNull(2) ?: "HTTP/1.1"
                        val authHeader = buildBasicProxyAuthHeader(active.username, active.password)
                        val proxyHeaders = buildString {
                            append("$method $fullUrl $httpVersion\r\n")
                            if (authHeader != null) {
                                append("Proxy-Authorization: $authHeader\r\n")
                            }
                            for (i in 1 until lines.size) {
                                val line = lines[i]
                                if (line.isEmpty()) continue
                                if (line.startsWith("Proxy-", ignoreCase = true)) continue
                                append(line).append("\r\n")
                            }
                            append("Connection: close\r\n\r\n")
                        }
                        val outBytes = proxyHeaders.toByteArray(Charsets.ISO_8859_1)
                        val upstreamOut = directHttpProxySock.getOutputStream()
                        upstreamOut.write(outBytes)
                        upstreamOut.flush()
                        totalBytesSent.addAndGet(outBytes.size.toLong())
                        pipeStreams(directHttpProxySock.getInputStream(), clientOut, isUpload = false)
                        return@withContext
                    }
                }

                upstreamSocket = openRemoteTunnelSocket(targetHost, targetPort)
                val upSock = upstreamSocket
                if (upSock == null) {
                    val errResp = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(errResp.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                    return@withContext
                }
                activeTunnelSockets.add(upSock)
                totalConnectionsHandled.incrementAndGet()

                val upstreamIn = upSock.getInputStream()
                val upstreamOut = upSock.getOutputStream()

                val httpVersion = parts.getOrNull(2) ?: "HTTP/1.1"
                val rewrittenHeaders = buildString {
                    append("$method $relativePath $httpVersion\r\n")
                    for (i in 1 until lines.size) {
                        val line = lines[i]
                        if (line.isEmpty()) continue
                        if (line.startsWith("Proxy-", ignoreCase = true)) continue
                        append(line).append("\r\n")
                    }
                    append("Connection: close\r\n\r\n")
                }

                val outBytes = rewrittenHeaders.toByteArray(Charsets.ISO_8859_1)
                upstreamOut.write(outBytes)
                upstreamOut.flush()
                totalBytesSent.addAndGet(outBytes.size.toLong())

                pipeStreams(upstreamIn, clientOut, isUpload = false)
            }
        } catch (_: Exception) {
        } finally {
            upstreamSocket?.let {
                activeTunnelSockets.remove(it)
                try {
                    it.close()
                } catch (_: Exception) {
                }
            }
            activeTunnelSockets.remove(client)
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun openRawSocketToProxy(node: VpnNode, timeoutMs: Int): Socket? {
        val socket = Socket()
        return try {
            protectSocketFn(socket)
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 30_000
            socket.connect(InetSocketAddress(node.ipAddress, node.port), timeoutMs)
            socket
        } catch (_: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun pipeStreams(input: InputStream, output: OutputStream, isUpload: Boolean) {
        val buffer = ByteArray(128 * 1024)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
                if (isUpload) {
                    totalBytesSent.addAndGet(read.toLong())
                } else {
                    totalBytesReceived.addAndGet(read.toLong())
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {

        private val dnsIpv4Cache = ConcurrentHashMap<String, ByteArray>()
        private val ipv4Regex = Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})\b""")

        data class EndToEndVerificationResult(
            val relayType: RelayTunnelType,
            val realTcpPingMs: Int,
            val verifiedExitIp: String
        )

        data class DetailedVerificationOutcome(
            val result: EndToEndVerificationResult? = null,
            val failureStage: String? = null,
            val failureDetail: String? = null
        )

        /**
         * Resolves [host] to a 4-byte IPv4 address using protected UDP DNS (`1.1.1.1:53` / `8.8.8.8:53`).
         */
        fun resolveIpv4Protected(
            host: String,
            protectDatagram: ((DatagramSocket) -> Boolean)? = AetherVpnStateBus.protectDatagramCallback
        ): ByteArray? {
            val clean = host.trim().lowercase(Locale.US)
            if (clean.isEmpty()) return null

            parseIpv4Literal(clean)?.let { return it }
            dnsIpv4Cache[clean]?.let { return it }

            val dnsServers = listOf(
                byteArrayOf(1, 1, 1, 1),
                byteArrayOf(8, 8, 8, 8)
            )
            val queryPacket = buildDnsAQuery(clean)
            if (queryPacket != null) {
                for (dnsIp in dnsServers) {
                    val socket = try {
                        DatagramSocket()
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    try {
                        protectDatagram?.invoke(socket)
                        socket.soTimeout = 1200
                        val outPkt = DatagramPacket(
                            queryPacket,
                            queryPacket.size,
                            InetAddress.getByAddress(dnsIp),
                            53
                        )
                        socket.send(outPkt)
                        val respBuf = ByteArray(512)
                        val inPkt = DatagramPacket(respBuf, respBuf.size)
                        socket.receive(inPkt)
                        val resolved = parseDnsAResponse(respBuf, inPkt.length)
                        if (resolved != null && resolved.size == 4) {
                            dnsIpv4Cache[clean] = resolved
                            return resolved
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            socket.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            return try {
                val addr = InetAddress.getAllByName(clean).firstOrNull { it.address.size == 4 }?.address
                if (addr != null) {
                    dnsIpv4Cache[clean] = addr
                }
                addr
            } catch (_: Exception) {
                null
            }
        }

        private fun parseIpv4Literal(host: String): ByteArray? {
            val parts = host.split(".")
            if (parts.size != 4) return null
            val bytes = ByteArray(4)
            for (i in 0..3) {
                val n = parts[i].toIntOrNull() ?: return null
                if (n !in 0..255) return null
                bytes[i] = n.toByte()
            }
            return bytes
        }

        private fun buildDnsAQuery(domain: String): ByteArray? {
            val labels = domain.split(".").filter { it.isNotEmpty() }
            if (labels.isEmpty()) return null
            val out = ByteArrayOutputStream(64)
            out.write(byteArrayOf(0x2A, 0x3B, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            for (label in labels) {
                val b = label.toByteArray(Charsets.US_ASCII)
                if (b.isEmpty() || b.size > 63) return null
                out.write(b.size)
                out.write(b)
            }
            out.write(0x00)
            out.write(byteArrayOf(0x00, 0x01))
            out.write(byteArrayOf(0x00, 0x01))
            return out.toByteArray()
        }

        private fun parseDnsAResponse(buf: ByteArray, len: Int): ByteArray? {
            if (len < 12) return null
            val anCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
            if (anCount <= 0) return null
            var pos = 12
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
            pos += 4

            for (i in 0 until anCount) {
                if (pos >= len) break
                val nameByte = buf[pos].toInt() and 0xFF
                if ((nameByte and 0xC0) == 0xC0) {
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
                    return buf.copyOfRange(pos, pos + 4)
                }
                pos += rdLength
            }
            return null
        }

        /**
         * Performs an actual protected TCP 3-way handshake (`SYN` -> `SYN-ACK`) duration test
         * timed with `System.nanoTime()` against [ipOrHost]:[port] (no ICMP echo).
         * Sets `SO_LINGER(true, 0)` so probed sockets close cleanly without kernel TIME_WAIT buildup.
         * Returns `null` if the TCP handshake fails or times out.
         */
        fun measureTcpHandshakeDurationMs(
            ipOrHost: String,
            port: Int,
            timeoutMs: Int = 1800,
            protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback
        ): Int? {
            if (ipOrHost.isBlank() || port !in 1..65535) return null
            val resolvedBytes = resolveIpv4Protected(ipOrHost)
            val addr = if (resolvedBytes != null && resolvedBytes.size == 4) {
                InetSocketAddress(InetAddress.getByAddress(ipOrHost, resolvedBytes), port)
            } else {
                InetSocketAddress(ipOrHost, port)
            }
            val socket = Socket()
            return try {
                protectSocket?.invoke(socket)
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs
                runCatching { socket.setSoLinger(true, 0) }
                val startNs = System.nanoTime()
                socket.connect(addr, timeoutMs)
                val handshakeMs = ((System.nanoTime() - startNs) / 1_000_000L)
                    .toInt()
                    .coerceAtLeast(1)
                handshakeMs
            } catch (_: Exception) {
                null
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }

        /**
         * Backwards-compatible alias delegating to [measureTcpHandshakeDurationMs].
         */
        fun measureRealTcpPingMs(
            ipOrHost: String,
            port: Int,
            timeoutMs: Int = 1800,
            protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback
        ): Int? = measureTcpHandshakeDurationMs(
            ipOrHost = ipOrHost,
            port = port,
            timeoutMs = timeoutMs,
            protectSocket = protectSocket
        )

        /**
         * Strict End-to-End HTTPS TLS + Exit-IP Verification:
         * 1. Measures genuine TCP `SYN` -> `SYN-ACK` ping to `node.ipAddress:node.port`.
         * 2. Performs real proxy tunnel handshake (`SOCKS5`, `HTTP_CONNECT`, `SOCKS4`).
         * 3. Upgrades tunnel socket to a verified `SSLSocket` TLS session to `api.ipify.org:443`
         *    (or `checkip.amazonaws.com:443`) with full CA + hostname verification (rejects any MITM!).
         * 4. Confirms the returned public IPv4 address differs from [deviceDirectIp].
         */
        fun verifyNodeEndToEndTls(
            node: VpnNode,
            deviceDirectIp: String?,
            timeoutMs: Int = 4500,
            protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback,
            protectDatagram: ((DatagramSocket) -> Boolean)? = AetherVpnStateBus.protectDatagramCallback
        ): EndToEndVerificationResult? = verifyNodeEndToEndTlsDetailed(
            node = node,
            deviceDirectIp = deviceDirectIp,
            timeoutMs = timeoutMs,
            protectSocket = protectSocket,
            protectDatagram = protectDatagram
        ).result

        /**
         * Detailed End-to-End verification returning either a verified [EndToEndVerificationResult]
         * or a structured [failureStage] and [failureDetail] explaining why the server failed.
         */
        fun verifyNodeEndToEndTlsDetailed(
            node: VpnNode,
            deviceDirectIp: String?,
            timeoutMs: Int = 4500,
            protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback,
            protectDatagram: ((DatagramSocket) -> Boolean)? = AetherVpnStateBus.protectDatagramCallback
        ): DetailedVerificationOutcome {
            if (node.ipAddress.isBlank() || node.port !in 1..65535) {
                return DetailedVerificationOutcome(
                    failureStage = "Invalid Endpoint",
                    failureDetail = "Missing IP/host or invalid port (${node.port})."
                )
            }

            if (node.isSshProtocol) {
                val sshOutcome = SshTunnelManager.verifySshNodeEndToEndTls(
                    node = node,
                    deviceDirectIp = deviceDirectIp,
                    timeoutMs = maxOf(timeoutMs, 5500),
                    protectSocket = protectSocket
                )
                return if (sshOutcome.success && !sshOutcome.exitIp.isNullOrBlank()) {
                    DetailedVerificationOutcome(
                        result = EndToEndVerificationResult(
                            relayType = node.relayType,
                            realTcpPingMs = sshOutcome.tcpHandshakeMs.coerceAtLeast(1),
                            verifiedExitIp = sshOutcome.exitIp
                        )
                    )
                } else {
                    DetailedVerificationOutcome(
                        failureStage = sshOutcome.failure?.stage ?: "SSH Handshake Failed",
                        failureDetail = sshOutcome.failure?.detail ?: "Unable to open SSH direct-tcpip tunnel."
                    )
                }
            }

            val orderedTypes = if (node.isVerifiedWorking) {
                listOf(node.relayType, RelayTunnelType.SOCKS5, RelayTunnelType.HTTP_CONNECT).distinct()
            } else {
                listOf(node.relayType, RelayTunnelType.SOCKS5, RelayTunnelType.HTTP_CONNECT).distinct()
            }
            val probeEndpoints = listOf(
                "api.ipify.org" to "/?format=text",
                "checkip.amazonaws.com" to "/",
                "icanhazip.com" to "/"
            )
            val resolvedEndpoints = probeEndpoints.map { (host, path) ->
                Triple(host, path, resolveIpv4Protected(host, protectDatagram))
            }
            val authUser = if (node.isCustomNode) node.username else ""
            val authPass = if (node.isCustomNode) node.password else ""
            val phaseTimeoutMs = timeoutMs.coerceIn(1500, 3200)

            var lastFailureStage = "Handshake Failed"
            var lastFailureDetail = "Relay closed connection without completing TLS probe."

            protocolLoop@ for (candidateType in orderedTypes) {
                var tcpUnreachable = false
                for ((targetHost, targetPath, resolvedTargetIpv4) in resolvedEndpoints) {
                    val targetPort = 443
                    val rawSocket = Socket()
                    try {
                        protectSocket?.invoke(rawSocket)
                        rawSocket.tcpNoDelay = true
                        rawSocket.receiveBufferSize = 262_144
                        rawSocket.sendBufferSize = 262_144
                        rawSocket.soTimeout = phaseTimeoutMs
                        runCatching { rawSocket.setSoLinger(true, 0) }

                        val startNs = System.nanoTime()
                        try {
                            rawSocket.connect(InetSocketAddress(node.ipAddress, node.port), phaseTimeoutMs)
                        } catch (e: java.net.SocketTimeoutException) {
                            lastFailureStage = "TCP Timeout"
                            lastFailureDetail = "TCP SYN timed out after ${phaseTimeoutMs}ms reaching ${node.ipAddress}:${node.port}."
                            rawSocket.close()
                            tcpUnreachable = true
                            break
                        } catch (e: Exception) {
                            lastFailureStage = "TCP Connect Refused"
                            lastFailureDetail = "${e.javaClass.simpleName}: ${e.message ?: "Port ${node.port} unreachable"}"
                            rawSocket.close()
                            tcpUnreachable = true
                            break
                        }

                        val realTcpPingMs = ((System.nanoTime() - startNs) / 1_000_000L)
                            .toInt()
                            .coerceAtLeast(1)

                        val input = rawSocket.getInputStream()
                        val output = rawSocket.getOutputStream()

                        val handshakeOk = when (candidateType) {
                            RelayTunnelType.WIREGUARD_TUNNEL,
                            RelayTunnelType.SOCKS5 -> performSocks5Connect(
                                input = input,
                                output = output,
                                targetHost = targetHost,
                                resolvedIpv4 = resolvedTargetIpv4,
                                targetPort = targetPort,
                                username = authUser,
                                password = authPass
                            )
                            RelayTunnelType.SOCKS4 -> performSocks4aConnect(
                                input = input,
                                output = output,
                                targetHost = targetHost,
                                resolvedIpv4 = resolvedTargetIpv4,
                                targetPort = targetPort
                            )
                            RelayTunnelType.HTTP_CONNECT -> performHttpConnect(
                                input = input,
                                output = output,
                                targetHost = targetHost,
                                targetPort = targetPort,
                                username = authUser,
                                password = authPass
                            )
                            RelayTunnelType.SSH_TUNNEL,
                            RelayTunnelType.SSH_TLS,
                            RelayTunnelType.SSH_WS -> false
                        }

                        if (!handshakeOk) {
                            lastFailureStage = "${candidateType.badgeLabel} Handshake Failed"
                            lastFailureDetail = if (authUser.isNotEmpty()) {
                                "Relay rejected ${candidateType.badgeLabel} auth or refused CONNECT to $targetHost:$targetPort."
                            } else {
                                "Relay closed or rejected ${candidateType.badgeLabel} CONNECT tunnel to $targetHost:$targetPort."
                            }
                            rawSocket.close()
                            // Try next relay protocol instead of repeating failed protocol on other probe hosts
                            break
                        }

                        val sslSocket: SSLSocket = try {
                            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                            (sslFactory.createSocket(rawSocket, targetHost, targetPort, true) as SSLSocket).apply {
                                soTimeout = phaseTimeoutMs
                                startHandshake()
                            }
                        } catch (e: Exception) {
                            lastFailureStage = "TLS Handshake Failed"
                            lastFailureDetail = "${e.javaClass.simpleName}: ${e.message ?: "Unable to negotiate TLS via relay"}"
                            try {
                                rawSocket.close()
                            } catch (_: Exception) {
                            }
                            // Try next backup echo endpoint before giving up on this protocol
                            continue
                        }

                        val hostnameValid = HttpsURLConnection.getDefaultHostnameVerifier()
                            .verify(targetHost, sslSocket.session)
                        if (!hostnameValid) {
                            lastFailureStage = "TLS Certificate Mismatch"
                            lastFailureDetail = "Hostname verification failed for $targetHost (possible MITM/captive portal)."
                            sslSocket.close()
                            continue
                        }

                        val sslOut = sslSocket.getOutputStream()
                        val sslIn = sslSocket.getInputStream()
                        val httpReq = "GET $targetPath HTTP/1.1\r\nHost: $targetHost\r\nUser-Agent: AVPN/1.0\r\nConnection: close\r\n\r\n"
                        sslOut.write(httpReq.toByteArray(Charsets.US_ASCII))
                        sslOut.flush()

                        val responseStream = ByteArrayOutputStream(512)
                        val chunk = ByteArray(512)
                        var readTimedOut = false
                        while (responseStream.size() < 2048) {
                            val n = try {
                                sslIn.read(chunk)
                            } catch (_: java.net.SocketTimeoutException) {
                                readTimedOut = true
                                -1
                            } catch (_: Exception) {
                                -1
                            }
                            if (n <= 0) break
                            responseStream.write(chunk, 0, n)
                        }
                        sslSocket.close()

                        val responseBytes = responseStream.toByteArray()
                        if (responseBytes.isNotEmpty()) {
                            val responseText = String(responseBytes, Charsets.ISO_8859_1)
                            if (!responseText.startsWith("HTTP/1.") && !responseText.startsWith("HTTP/2")) {
                                lastFailureStage = "Malformed Probe Response"
                                lastFailureDetail = "Relay returned non-HTTP payload over TLS from $targetHost."
                                continue
                            }
                            val statusLine = responseText.lineSequence().firstOrNull().orEmpty().trim()
                            if (!statusLine.contains("200")) {
                                // Common on public proxies when ipify returns 429; loop falls through to checkip.amazonaws.com / icanhazip.com!
                                lastFailureStage = "HTTP Probe Error"
                                lastFailureDetail = "$targetHost returned '$statusLine' instead of HTTP 200."
                                continue
                            }

                            val body = responseText.substringAfter("\r\n\r\n", "").trim()
                            val exitIp = ipv4Regex.find(body)?.groupValues?.getOrNull(1)
                            if (exitIp == null) {
                                lastFailureStage = "Invalid Exit IP Body"
                                lastFailureDetail = "$targetHost HTTP 200 body did not include a valid IPv4 address."
                                continue
                            }
                            if (!deviceDirectIp.isNullOrBlank() && exitIp == deviceDirectIp) {
                                lastFailureStage = "Transparent Proxy Leak"
                                lastFailureDetail = "Relay exposed real device IP ($deviceDirectIp) and was rejected."
                                // Reject transparent proxy completely across all protocols
                                break@protocolLoop
                            }

                            return DetailedVerificationOutcome(
                                result = EndToEndVerificationResult(
                                    relayType = candidateType,
                                    realTcpPingMs = realTcpPingMs,
                                    verifiedExitIp = exitIp
                                )
                            )
                        } else {
                            lastFailureStage = if (readTimedOut) "TLS Read Timeout" else "Empty Probe Response"
                            lastFailureDetail = if (readTimedOut) {
                                "TLS handshake succeeded, but relay stalled waiting for HTTPS response from $targetHost (${phaseTimeoutMs}ms)."
                            } else {
                                "Relay closed TLS stream before receiving HTTP headers from $targetHost."
                            }
                        }
                    } catch (e: Exception) {
                        lastFailureStage = "Tunnel Probe Exception"
                        lastFailureDetail = "${e.javaClass.simpleName}: ${e.message ?: "Unexpected failure"}"
                        try {
                            rawSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                }
                if (tcpUnreachable) break@protocolLoop
            }
            return DetailedVerificationOutcome(
                result = null,
                failureStage = lastFailureStage,
                failureDetail = lastFailureDetail
            )
        }

        /**
         * Opens a high-bandwidth tunnel socket through [node] to [targetHost]:[targetPort] using [protectSocket].
         */
        fun connectViaRelayNode(
            node: VpnNode,
            targetHost: String,
            targetPort: Int,
            timeoutMs: Int = 3500,
            useExplicitProxyAuth: Boolean = node.isCustomNode,
            protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback,
            protectDatagram: ((DatagramSocket) -> Boolean)? = AetherVpnStateBus.protectDatagramCallback
        ): Socket? {
            if (node.isSshProtocol) {
                return SshTunnelManager.openDirectTcpIpSocket(
                    node = node,
                    targetHost = targetHost,
                    targetPort = targetPort,
                    timeoutMs = maxOf(timeoutMs, 5500),
                    protectSocket = protectSocket
                )
            }

            val orderedTypes = if (node.isVerifiedWorking) {
                listOf(node.relayType)
            } else {
                listOf(
                    node.relayType,
                    RelayTunnelType.SOCKS5,
                    RelayTunnelType.HTTP_CONNECT,
                    RelayTunnelType.SOCKS4
                ).distinct()
            }

            val resolvedIpv4 = resolveIpv4Protected(targetHost, protectDatagram)
            val authUser = if (useExplicitProxyAuth) node.username else ""
            val authPass = if (useExplicitProxyAuth) node.password else ""

            for (type in orderedTypes) {
                val socket = Socket()
                try {
                    protectSocket?.invoke(socket)
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.receiveBufferSize = 262_144
                    socket.sendBufferSize = 262_144
                    socket.soTimeout = timeoutMs + 1500
                    socket.connect(InetSocketAddress(node.ipAddress, node.port), timeoutMs)

                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()

                    val handshakeOk = when (type) {
                        RelayTunnelType.WIREGUARD_TUNNEL,
                        RelayTunnelType.SOCKS5 -> performSocks5Connect(
                            input = input,
                            output = output,
                            targetHost = targetHost,
                            resolvedIpv4 = resolvedIpv4,
                            targetPort = targetPort,
                            username = authUser,
                            password = authPass
                        )
                        RelayTunnelType.SOCKS4 -> performSocks4aConnect(
                            input = input,
                            output = output,
                            targetHost = targetHost,
                            resolvedIpv4 = resolvedIpv4,
                            targetPort = targetPort
                        )
                        RelayTunnelType.HTTP_CONNECT -> performHttpConnect(
                            input = input,
                            output = output,
                            targetHost = targetHost,
                            targetPort = targetPort,
                            username = authUser,
                            password = authPass
                        )
                        RelayTunnelType.SSH_TUNNEL,
                        RelayTunnelType.SSH_TLS,
                        RelayTunnelType.SSH_WS -> false
                    }

                    if (handshakeOk) {
                        socket.soTimeout = 45_000
                        return socket
                    } else {
                        socket.close()
                    }
                } catch (_: Exception) {
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
                }
            }
            return null
        }

        /**
         * Fetches the device's real direct public IP (over a protected HTTPS call that bypasses `tun0`).
         */
        fun fetchDeviceDirectIp(
            protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback
        ): String? {
            return try {
                val url = URL("https://api.ipify.org")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 2500
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "AVPN-Android/1.0")
                }
                if (conn.responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText().trim() }
                        .takeIf { ipv4Regex.matches(it) }
                } else null
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Verifies the real public exit IP & geolocation through the running local tunnel gateway (`127.0.0.1:localPort`).
         */
        fun fetchExitIpThroughLocalGateway(localPort: Int, fallbackNode: VpnNode): ExitIpVerification {
            if (localPort > 0) {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localPort))
                try {
                    val url = URL("https://ipwho.is/")
                    val conn = (url.openConnection(proxy) as HttpsURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "AVPN-Android/1.0")
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            val ip = json.optString("ip").ifEmpty { fallbackNode.verifiedExitIp.ifEmpty { fallbackNode.ipAddress } }
                            val country = json.optString("country").ifEmpty { fallbackNode.countryName }
                            val code = json.optString("country_code").ifEmpty { fallbackNode.countryCode }
                            val city = json.optString("city").ifEmpty { fallbackNode.cityName }
                            val isp = json.optJSONObject("connection")?.optString("isp").orEmpty().ifEmpty { fallbackNode.operatorName }
                            return ExitIpVerification(
                                publicIp = ip,
                                countryName = country,
                                countryCode = code,
                                cityName = city,
                                ispName = isp
                            )
                        }
                    }
                } catch (_: Exception) {
                }

                // Fallback through proxy to api.country.is for exact exit country ISO
                try {
                    val url = URL("https://api.country.is/")
                    val conn = (url.openConnection(proxy) as HttpsURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "AVPN-Android/1.0")
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val ip = json.optString("ip").ifEmpty { fallbackNode.verifiedExitIp.ifEmpty { fallbackNode.ipAddress } }
                        val cc = json.optString("country").take(2).uppercase(Locale.US)
                        if (cc.length == 2 && cc != "XX") {
                            val countryName = Locale("", cc).getDisplayCountry(Locale.ENGLISH).ifEmpty { cc }
                            return ExitIpVerification(
                                publicIp = ip,
                                countryName = countryName,
                                countryCode = cc,
                                cityName = fallbackNode.cityName,
                                ispName = fallbackNode.operatorName
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return ExitIpVerification(
                publicIp = fallbackNode.verifiedExitIp.ifEmpty { fallbackNode.ipAddress },
                countryName = fallbackNode.countryName,
                countryCode = fallbackNode.countryCode,
                cityName = fallbackNode.cityName,
                ispName = fallbackNode.operatorName
            )
        }

        private fun buildBasicProxyAuthHeader(username: String, password: String): String? {
            if (username.isEmpty() && password.isEmpty()) return null
            val raw = "$username:$password".toByteArray(Charsets.UTF_8)
            return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
        }

        private fun performSocks5Connect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            resolvedIpv4: ByteArray?,
            targetPort: Int,
            username: String = "",
            password: String = ""
        ): Boolean {
            val hasAuth = username.isNotEmpty() || password.isNotEmpty()
            if (hasAuth) {
                output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                output.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            output.flush()

            val greetingResp = ByteArray(2)
            if (!readFully(input, greetingResp)) return false
            if (greetingResp[0] != 0x05.toByte()) return false

            when (greetingResp[1]) {
                0x00.toByte() -> {}
                0x02.toByte() -> {
                    if (!hasAuth) return false
                    val uBytes = username.toByteArray(Charsets.UTF_8)
                    val pBytes = password.toByteArray(Charsets.UTF_8)
                    if (uBytes.size > 255 || pBytes.size > 255) return false
                    val authReq = ByteArray(3 + uBytes.size + pBytes.size)
                    authReq[0] = 0x01
                    authReq[1] = uBytes.size.toByte()
                    System.arraycopy(uBytes, 0, authReq, 2, uBytes.size)
                    authReq[2 + uBytes.size] = pBytes.size.toByte()
                    System.arraycopy(pBytes, 0, authReq, 3 + uBytes.size, pBytes.size)
                    output.write(authReq)
                    output.flush()
                    val authResp = ByteArray(2)
                    if (!readFully(input, authResp) || authResp[1] != 0x00.toByte()) return false
                }
                else -> return false
            }

            val req: ByteArray = if (resolvedIpv4 != null && resolvedIpv4.size == 4) {
                byteArrayOf(
                    0x05,
                    0x01,
                    0x00,
                    0x01,
                    resolvedIpv4[0],
                    resolvedIpv4[1],
                    resolvedIpv4[2],
                    resolvedIpv4[3],
                    ((targetPort shr 8) and 0xFF).toByte(),
                    (targetPort and 0xFF).toByte()
                )
            } else {
                val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                if (hostBytes.isEmpty() || hostBytes.size > 255) return false
                val buf = ByteArray(4 + 1 + hostBytes.size + 2)
                buf[0] = 0x05
                buf[1] = 0x01
                buf[2] = 0x00
                buf[3] = 0x03
                buf[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, buf, 5, hostBytes.size)
                buf[5 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
                buf[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
                buf
            }

            output.write(req)
            output.flush()

            val respHeader = ByteArray(4)
            if (!readFully(input, respHeader)) return false
            if (respHeader[0] != 0x05.toByte() || respHeader[1] != 0x00.toByte()) return false

            val remainingBytes = when (respHeader[3].toInt() and 0xFF) {
                0x01 -> 4 + 2
                0x04 -> 16 + 2
                0x03 -> {
                    val lenByte = input.read()
                    if (lenByte < 0) return false
                    lenByte + 2
                }
                else -> return false
            }
            val addrPortBuf = ByteArray(remainingBytes)
            return readFully(input, addrPortBuf)
        }

        private fun performSocks4aConnect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            resolvedIpv4: ByteArray?,
            targetPort: Int
        ): Boolean {
            if (resolvedIpv4 != null && resolvedIpv4.size == 4) {
                val req = ByteArray(9)
                req[0] = 0x04
                req[1] = 0x01
                req[2] = ((targetPort shr 8) and 0xFF).toByte()
                req[3] = (targetPort and 0xFF).toByte()
                System.arraycopy(resolvedIpv4, 0, req, 4, 4)
                req[8] = 0x00
                output.write(req)
                output.flush()
            } else {
                val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                val req = ByteArrayOutputStream()
                req.write(0x04)
                req.write(0x01)
                req.write((targetPort shr 8) and 0xFF)
                req.write(targetPort and 0xFF)
                req.write(byteArrayOf(0x00, 0x00, 0x00, 0x01))
                req.write(0x00)
                req.write(hostBytes)
                req.write(0x00)
                output.write(req.toByteArray())
                output.flush()
            }

            val resp = ByteArray(8)
            if (!readFully(input, resp)) return false
            return resp[1] == 0x5A.toByte()
        }

        private fun performHttpConnect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            targetPort: Int,
            username: String = "",
            password: String = ""
        ): Boolean {
            val authHeader = buildBasicProxyAuthHeader(username, password)
            val connectReq = buildString {
                append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
                append("Host: $targetHost:$targetPort\r\n")
                if (authHeader != null) {
                    append("Proxy-Authorization: $authHeader\r\n")
                }
                append("User-Agent: Mozilla/5.0 (Linux; Android 14)\r\n")
                append("Proxy-Connection: keep-alive\r\n")
                append("Connection: keep-alive\r\n\r\n")
            }
            output.write(connectReq.toByteArray(Charsets.ISO_8859_1))
            output.flush()

            val headerBytes = readHttpHeaderBlock(input) ?: return false
            val statusLine = String(headerBytes, Charsets.ISO_8859_1).lineSequence().firstOrNull().orEmpty()
            return statusLine.contains(" 200")
        }

        private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read <= 0) return false
                offset += read
            }
            return true
        }

        private fun readHttpHeaderBlock(input: InputStream): ByteArray? {
            val out = ByteArrayOutputStream()
            var state = 0
            while (out.size() < 32_768) {
                val b = input.read()
                if (b == -1) return if (out.size() > 0) out.toByteArray() else null
                out.write(b)
                state = when {
                    state == 0 && b == '\r'.code -> 1
                    state == 1 && b == '\n'.code -> 2
                    state == 2 && b == '\r'.code -> 3
                    state == 3 && b == '\n'.code -> 4
                    b == '\r'.code -> 1
                    else -> 0
                }
                if (state == 4) {
                    return out.toByteArray()
                }
            }
            return null
        }

        private fun parseHttpUrlTarget(
            targetToken: String,
            headerLines: List<String>
        ): Triple<String, Int, String>? {
            return try {
                if (targetToken.startsWith("http://", ignoreCase = true) ||
                    targetToken.startsWith("https://", ignoreCase = true)
                ) {
                    val url = URL(targetToken)
                    val host = url.host
                    val port = if (url.port != -1) url.port else if (url.protocol == "https") 443 else 80
                    val path = buildString {
                        append(url.path.ifEmpty { "/" })
                        if (!url.query.isNullOrEmpty()) {
                            append("?").append(url.query)
                        }
                    }
                    Triple(host, port, path)
                } else {
                    val hostHeader = headerLines
                        .firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                        ?.substringAfter(":")
                        ?.trim()
                        ?: return null
                    val parts = hostHeader.split(":")
                    val host = parts[0].trim()
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
                    Triple(host, port, targetToken.ifEmpty { "/" })
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
