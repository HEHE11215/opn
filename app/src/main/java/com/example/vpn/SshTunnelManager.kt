package com.example.vpn

import android.util.Base64
import com.example.model.RelayTunnelType
import com.example.model.VpnNode
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Genuine SSH-2 / SSH-over-TLS / SSH-over-WebSocket Tunnel Engine backed by JSch (`com.github.mwiede:jsch`).
 *
 * Features:
 * 1. **Real SSH-2 Multiplexed Sessions**: Maintains a pooled `Session` per endpoint with `serverAliveInterval`
 *    so dozens of concurrent TCP/HTTPS streams multiplex over one authenticated SSH connection at full bandwidth.
 * 2. **Multi-Transport Support**:
 *    - [RelayTunnelType.SSH_TUNNEL]: Direct OpenSSH / Dropbear (`port 22 / 2222`).
 *    - [RelayTunnelType.SSH_TLS]: SSH-2 encapsulated inside TLS 1.3 / 1.2 with custom SNI on port `443`.
 *    - [RelayTunnelType.SSH_WS]: SSH-2 encapsulated inside HTTP `101 Switching Protocols` WebSocket upgrade.
 * 3. **Password & Private Key Auth**: Supports password login and PEM/OpenSSH private keys (`Ed25519`/`RSA`/`ECDSA`).
 * 4. **Protected Sockets**: Calls [VpnService.protect] before TCP connect to prevent `tun0` routing loops.
 */
object SshTunnelManager {

    private val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionPool = ConcurrentHashMap<String, Session>()
    private val sessionLocks = ConcurrentHashMap<String, Any>()
    private val secureRandom = SecureRandom()

    data class SshProbeFailure(
        val stage: String,
        val detail: String
    )

    data class SshProbeOutcome(
        val success: Boolean,
        val tcpHandshakeMs: Int = -1,
        val exitIp: String? = null,
        val failure: SshProbeFailure? = null
    )

    private fun sessionPoolKey(node: VpnNode): String {
        return "${node.ipAddress}:${node.port}:${node.relayType.name}:${node.username}:${node.sniHost}"
    }

    /**
     * Returns an active, authenticated JSch [Session] for [node] from the pool or creates a new one.
     */
    @Throws(Exception::class)
    fun getOrConnectSession(
        node: VpnNode,
        timeoutMs: Int = 5500,
        protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback
    ): Session {
        val key = sessionPoolKey(node)
        val lock = sessionLocks.getOrPut(key) { Any() }

        synchronized(lock) {
            val existing = sessionPool[key]
            if (existing != null && existing.isConnected) {
                return existing
            }
            if (existing != null) {
                runCatching { existing.disconnect() }
                sessionPool.remove(key)
            }

            val jsch = JSch()
            if (node.sshPrivateKey.isNotBlank()) {
                val cleanPem = node.sshPrivateKey.trim().toByteArray(Charsets.UTF_8)
                jsch.addIdentity("avpn_key_${node.countryCode}", cleanPem, null, null)
            }

            val connectPort = when (node.relayType) {
                RelayTunnelType.SSH_TLS -> if (node.port == 22) node.tlsPort.coerceIn(1, 65535) else node.port
                else -> node.port
            }

            val sshUser = node.username.ifBlank { "root" }
            val session = jsch.getSession(sshUser, node.ipAddress, connectPort)
            if (node.password.isNotBlank()) {
                session.setPassword(node.password)
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("Compression", "yes")
                put("CompressionLevel", "6")
                put(
                    "PreferredAuthentications",
                    if (node.sshPrivateKey.isNotBlank()) {
                        "publickey,password,keyboard-interactive"
                    } else {
                        "password,keyboard-interactive,publickey"
                    }
                )
            }
            session.setConfig(config)
            session.setSocketFactory(ProtectedTransportSocketFactory(node, timeoutMs, protectSocket))
            session.timeout = timeoutMs
            session.serverAliveInterval = 15_000
            session.serverAliveCountMax = 3

            session.connect(timeoutMs)
            sessionPool[key] = session
            return session
        }
    }

    /**
     * Opens a live `direct-tcpip` encrypted stream through the remote SSH server to [targetHost]:[targetPort]
     * and returns a bridged `java.net.Socket` ready for `LocalTunnelGateway` or TLS wrapping.
     */
    fun openDirectTcpIpSocket(
        node: VpnNode,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 5500,
        protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback
    ): Socket? {
        var channel: ChannelDirectTCPIP? = null
        var pumpSocket: Socket? = null
        var clientSocket: Socket? = null
        try {
            val session = getOrConnectSession(node, timeoutMs, protectSocket)
            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(0)

            val sshIn = channel.inputStream
            val sshOut = channel.outputStream
            channel.connect(timeoutMs)

            // Create an ultra-fast ephemeral loopback socket pair on 127.0.0.1
            val loopbackAddr = InetAddress.getByName("127.0.0.1")
            ServerSocket(0, 1, loopbackAddr).use { listener ->
                listener.soTimeout = 1500
                clientSocket = Socket().apply {
                    tcpNoDelay = true
                    receiveBufferSize = 262_144
                    sendBufferSize = 262_144
                    connect(InetSocketAddress(loopbackAddr, listener.localPort), 1500)
                }
                pumpSocket = listener.accept().apply {
                    tcpNoDelay = true
                    receiveBufferSize = 262_144
                    sendBufferSize = 262_144
                }
            }

            val activePump = pumpSocket!!
            val activeChannel = channel

            // Bidirectionally pump SSH direct-tcpip <-> loopback socket
            pumpScope.launch {
                try {
                    val buf = ByteArray(128 * 1024)
                    val out = activePump.getOutputStream()
                    while (true) {
                        val n = sshIn.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        out.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { activePump.shutdownOutput() }
                }
            }

            pumpScope.launch {
                try {
                    val buf = ByteArray(128 * 1024)
                    val inp = activePump.getInputStream()
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        sshOut.write(buf, 0, n)
                        sshOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { sshOut.close() }
                    runCatching { activeChannel.disconnect() }
                    runCatching { activePump.close() }
                }
            }

            return clientSocket
        } catch (_: Exception) {
            runCatching { channel?.disconnect() }
            runCatching { pumpSocket?.close() }
            runCatching { clientSocket?.close() }
            return null
        }
    }

    /**
     * Performs strict End-to-End SSH-2 authentication + `direct-tcpip` HTTPS TLS verification
     * against `api.ipify.org:443` and confirms the real exit IP differs from [deviceDirectIp].
     */
    fun verifySshNodeEndToEndTls(
        node: VpnNode,
        deviceDirectIp: String?,
        timeoutMs: Int = 6000,
        protectSocket: ((Socket) -> Boolean)? = AetherVpnStateBus.protectSocketCallback
    ): SshProbeOutcome {
        if (node.ipAddress.isBlank() || node.port !in 1..65535) {
            return SshProbeOutcome(
                success = false,
                failure = SshProbeFailure("Invalid SSH Endpoint", "Missing host/IP or invalid port (${node.port}).")
            )
        }
        if (node.username.isBlank() || (node.password.isBlank() && node.sshPrivateKey.isBlank())) {
            return SshProbeOutcome(
                success = false,
                failure = SshProbeFailure(
                    "SSH Account Required",
                    "No valid SSH username/password or SSH private key for ${node.ipAddress}."
                )
            )
        }

        val startNs = System.nanoTime()
        try {
            val session = getOrConnectSession(node, timeoutMs, protectSocket)
            if (!session.isConnected) {
                return SshProbeOutcome(
                    success = false,
                    failure = SshProbeFailure("SSH Handshake Failed", "SSH session disconnected before auth completed.")
                )
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val stage = when {
                msg.contains("Auth fail", ignoreCase = true) -> "SSH Auth Expired/Rejected"
                msg.contains("timeout", ignoreCase = true) -> "SSH Connect Timeout"
                else -> "SSH Handshake Failed"
            }
            return SshProbeOutcome(
                success = false,
                failure = SshProbeFailure(stage, msg.ifEmpty { e.javaClass.simpleName })
            )
        }

        val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)

        val tunnelSock = openDirectTcpIpSocket(
            node = node,
            targetHost = "api.ipify.org",
            targetPort = 443,
            timeoutMs = timeoutMs,
            protectSocket = protectSocket
        ) ?: return SshProbeOutcome(
            success = false,
            tcpHandshakeMs = rttMs,
            failure = SshProbeFailure(
                "SSH Forwarding Denied",
                "SSH authenticated, but server rejected direct-tcpip forwarding to api.ipify.org:443."
            )
        )

        return try {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSock = (sslFactory.createSocket(tunnelSock, "api.ipify.org", 443, true) as SSLSocket).apply {
                soTimeout = timeoutMs
                startHandshake()
            }
            val out = sslSock.getOutputStream()
            val inp = sslSock.getInputStream()
            val req = "GET /?format=text HTTP/1.1\r\nHost: api.ipify.org\r\nUser-Agent: AVPN-SSH/1.0\r\nConnection: close\r\n\r\n"
            out.write(req.toByteArray(Charsets.US_ASCII))
            out.flush()

            val baos = ByteArrayOutputStream(256)
            val buf = ByteArray(512)
            while (baos.size() < 2048) {
                val n = runCatching { inp.read(buf) }.getOrDefault(-1)
                if (n <= 0) break
                baos.write(buf, 0, n)
            }
            sslSock.close()

            val body = String(baos.toByteArray(), Charsets.ISO_8859_1)
                .substringAfter("\r\n\r\n", "")
                .trim()
            val ipv4 = Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})\b""").find(body)?.groupValues?.getOrNull(1)
            if (ipv4 == null) {
                SshProbeOutcome(
                    success = false,
                    tcpHandshakeMs = rttMs,
                    failure = SshProbeFailure("SSH Exit Probe Invalid", "SSH tunnel returned non-IP payload from api.ipify.org.")
                )
            } else if (!deviceDirectIp.isNullOrBlank() && ipv4 == deviceDirectIp) {
                SshProbeOutcome(
                    success = false,
                    tcpHandshakeMs = rttMs,
                    failure = SshProbeFailure("Transparent SSH Leak", "SSH exit IP matched real device IP ($deviceDirectIp).")
                )
            } else {
                SshProbeOutcome(
                    success = true,
                    tcpHandshakeMs = rttMs,
                    exitIp = ipv4
                )
            }
        } catch (e: Exception) {
            runCatching { tunnelSock.close() }
            SshProbeOutcome(
                success = false,
                tcpHandshakeMs = rttMs,
                failure = SshProbeFailure("SSH TLS Probe Failed", e.message ?: e.javaClass.simpleName)
            )
        }
    }

    /**
     * Closes all cached SSH sessions when disconnecting or destroying [AetherVpnService].
     */
    fun closeAllSessions() {
        val entries = sessionPool.entries.toList()
        sessionPool.clear()
        for ((_, session) in entries) {
            runCatching { session.disconnect() }
        }
    }

    /**
     * Custom JSch [SocketFactory] supporting protected raw TCP (`SSH_TUNNEL`), TLS SNI Stunnel (`SSH_TLS`),
     * and HTTP `Upgrade: websocket` (`SSH_WS`).
     */
    private class ProtectedTransportSocketFactory(
        private val node: VpnNode,
        private val timeoutMs: Int,
        private val protectSocket: ((Socket) -> Boolean)?
    ) : SocketFactory {

        override fun createSocket(host: String, port: Int): Socket {
            val raw = Socket()
            protectSocket?.invoke(raw)
            raw.tcpNoDelay = true
            raw.keepAlive = true
            raw.receiveBufferSize = 262_144
            raw.sendBufferSize = 262_144
            raw.soTimeout = timeoutMs
            raw.connect(InetSocketAddress(host, port), timeoutMs)

            return when (node.relayType) {
                RelayTunnelType.SSH_TLS -> {
                    val sniTarget = node.sniHost.ifBlank { host }
                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSock = sslFactory.createSocket(raw, sniTarget, port, true) as SSLSocket
                    try {
                        val params = sslSock.sslParameters
                        params.serverNames = listOf(SNIHostName(sniTarget))
                        sslSock.sslParameters = params
                    } catch (_: Exception) {
                    }
                    sslSock.soTimeout = timeoutMs
                    sslSock.startHandshake()
                    sslSock
                }

                RelayTunnelType.SSH_WS -> {
                    val transportSock: Socket = if (port == 443 || port == 8443) {
                        val sniTarget = node.sniHost.ifBlank { host }
                        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        (sslFactory.createSocket(raw, sniTarget, port, true) as SSLSocket).apply {
                            try {
                                val params = sslParameters
                                params.serverNames = listOf(SNIHostName(sniTarget))
                                sslParameters = params
                            } catch (_: Exception) {
                            }
                            soTimeout = timeoutMs
                            startHandshake()
                        }
                    } else {
                        raw
                    }

                    val wsHostHeader = node.sniHost.ifBlank { host }
                    val wsKeyBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
                    val wsKey = Base64.encodeToString(wsKeyBytes, Base64.NO_WRAP)
                    val upgradeReq = buildString {
                        append("GET / HTTP/1.1\r\n")
                        append("Host: $wsHostHeader\r\n")
                        append("Upgrade: websocket\r\n")
                        append("Connection: Upgrade\r\n")
                        append("Sec-WebSocket-Key: $wsKey\r\n")
                        append("Sec-WebSocket-Version: 13\r\n")
                        append("User-Agent: Mozilla/5.0 (Linux; Android 14)\r\n\r\n")
                    }
                    val out = transportSock.getOutputStream()
                    out.write(upgradeReq.toByteArray(Charsets.US_ASCII))
                    out.flush()

                    // Read HTTP 101 Switching Protocols response cleanly byte-by-byte until \r\n\r\n
                    val inp = transportSock.getInputStream()
                    val headerBuf = ByteArrayOutputStream(256)
                    var matchState = 0
                    while (headerBuf.size() < 4096) {
                        val b = inp.read()
                        if (b == -1) break
                        headerBuf.write(b)
                        matchState = when (matchState) {
                            0 -> if (b == '\r'.code) 1 else 0
                            1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                            2 -> if (b == '\r'.code) 3 else 0
                            3 -> if (b == '\n'.code) 4 else 0
                            else -> 0
                        }
                        if (matchState == 4) break
                    }
                    val statusHeader = String(headerBuf.toByteArray(), Charsets.ISO_8859_1)
                    if (!statusHeader.contains("101") && !statusHeader.contains("200")) {
                        runCatching { transportSock.close() }
                        throw IllegalStateException("SSH WebSocket upgrade failed: ${statusHeader.lineSequence().firstOrNull().orEmpty()}")
                    }
                    transportSock
                }

                else -> raw
            }
        }

        override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
    }
}
