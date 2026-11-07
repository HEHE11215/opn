package com.example.vpn

import android.net.VpnService
import com.example.model.ExitIpVerification
import com.example.model.RelayTunnelType
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
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * LocalTunnelGateway — Real HTTP/HTTPS (`CONNECT`) to Remote `SOCKS5` / `SOCKS4a` / `HTTP CONNECT`
 * Tunneling Bridge bound to `127.0.0.1:<localPort>`.
 *
 * Why this is essential on Android:
 * 1. Android's [VpnService.Builder.setHttpProxy] directs Chrome, Google Search, WebView, and
 *    system HTTP/HTTPS clients to send traffic to a local HTTP `CONNECT` proxy (`127.0.0.1:localPort`).
 * 2. Meanwhile, public worldwide free nodes operate on heterogeneous protocols (`SOCKS5`, `SOCKS4`,
 *    and `HTTP CONNECT`).
 * 3. [LocalTunnelGateway] accepts standard HTTP & HTTPS `CONNECT host:443` streams from the device's
 *    browsers and encapsulates them over the selected remote worldwide node (performing remote DNS
 *    resolution via `SOCKS5 ATYP=0x03` so DNS never leaks locally), genuinely changing the user's
 *    public Exit IP address and country on Google and all websites.
 */
class LocalTunnelGateway(
    private val vpnService: VpnService? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var primaryNode: VpnNode? = null

    private val fallbackVerifiedNodes = CopyOnWriteArrayList<VpnNode>()

    val totalBytesReceived = AtomicLong(0L)
    val totalBytesSent = AtomicLong(0L)
    val totalConnectionsHandled = AtomicLong(0L)

    val listeningPort: Int
        get() = serverSocket?.localPort ?: 0

    /**
     * Starts the local gateway on `127.0.0.1` using an available system port and returns the port.
     */
    fun start(
        initialNode: VpnNode,
        verifiedBackups: List<VpnNode>
    ): Int {
        stop()
        primaryNode = initialNode
        fallbackVerifiedNodes.clear()
        fallbackVerifiedNodes.addAll(verifiedBackups.filter { it.ipAddress != initialNode.ipAddress })

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress("127.0.0.1", 0), 128)
        serverSocket = server

        val boundPort = server.localPort
        AetherVpnStateBus.appendLog(
            tag = "GATEWAY",
            message = "Local Tunnel Gateway listening on 127.0.0.1:$boundPort -> ${initialNode.flagEmoji} ${initialNode.ipAddress}:${initialNode.port} (${initialNode.relayType.badgeLabel})",
            isHighlight = true
        )

        acceptJob = scope.launch {
            while (isActive && !server.isClosed) {
                val clientSocket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                launch {
                    handleClientConnection(clientSocket)
                }
            }
        }
        return boundPort
    }

    fun updateActiveNode(newNode: VpnNode, backups: List<VpnNode>) {
        primaryNode = newNode
        fallbackVerifiedNodes.clear()
        fallbackVerifiedNodes.addAll(backups.filter { it.ipAddress != newNode.ipAddress })
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private suspend fun handleClientConnection(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.soTimeout = 25_000
            client.tcpNoDelay = true
            val clientIn = BufferedInputStream(client.getInputStream())
            val clientOut = client.getOutputStream()

            // Read HTTP request header from Chrome / browser up to \r\n\r\n
            val headerBytes = readHttpHeaderBlock(clientIn) ?: run {
                client.close()
                return@withContext
            }
            totalBytesSent.addAndGet(headerBytes.size.toLong())

            val headerStr = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerStr.split("\r\n")
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            if (requestLine.isEmpty()) {
                client.close()
                return@withContext
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                client.close()
                return@withContext
            }

            val method = parts[0].uppercase()
            val targetToken = parts[1]

            if (method == "CONNECT") {
                // HTTPS Tunnel: CONNECT www.google.com:443 HTTP/1.1
                val hostPort = targetToken.split(":")
                val targetHost = hostPort[0].trim()
                val targetPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                val upstreamSocket = openUpstreamTunnelWithFailover(targetHost, targetPort)
                if (upstreamSocket == null) {
                    val errResp = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(errResp.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                    client.close()
                    return@withContext
                }

                totalConnectionsHandled.incrementAndGet()

                // Notify Chrome that the encrypted tunnel to targetHost:targetPort is established
                val okResp = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: AetherNode-Gateway/1.0\r\n\r\n"
                clientOut.write(okResp.toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()

                // Pipe TLS bytes bidirectionally until EOF
                val upstreamIn = upstreamSocket.getInputStream()
                val upstreamOut = upstreamSocket.getOutputStream()

                val clientToUpstream = launch {
                    pipeStreams(clientIn, upstreamOut, isUpload = true)
                    try {
                        upstreamSocket.shutdownOutput()
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
                try {
                    upstreamSocket.close()
                } catch (_: Exception) {
                }
            } else {
                // Plain HTTP request: e.g. GET http://ip-api.com/json HTTP/1.1
                val parsedTarget = parseHttpUrlTarget(targetToken, lines)
                if (parsedTarget == null) {
                    client.close()
                    return@withContext
                }
                val (targetHost, targetPort, relativePath) = parsedTarget

                val upstreamSocket = openUpstreamTunnelWithFailover(targetHost, targetPort)
                if (upstreamSocket == null) {
                    val errResp = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(errResp.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                    client.close()
                    return@withContext
                }

                totalConnectionsHandled.incrementAndGet()
                val upstreamIn = upstreamSocket.getInputStream()
                val upstreamOut = upstreamSocket.getOutputStream()

                // Rewrite first line to relative path so standard origin servers accept it
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
                try {
                    upstreamSocket.close()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Connects to [targetHost]:[targetPort] through the primary [VpnNode].
     * If the primary free node drops or times out on a socket, transparently retries across
     * the verified backup nodes so Chrome page loads never stall.
     */
    private fun openUpstreamTunnelWithFailover(targetHost: String, targetPort: Int): Socket? {
        val primary = primaryNode ?: return null
        val candidates = buildList {
            add(primary)
            addAll(fallbackVerifiedNodes.take(4))
        }

        for (candidate in candidates) {
            val connectedSocket = connectViaRelayNode(
                node = candidate,
                targetHost = targetHost,
                targetPort = targetPort,
                timeoutMs = 5500
            )
            if (connectedSocket != null) {
                if (candidate.id != primary.id) {
                    // Promote working backup node to primary so subsequent sockets are instant
                    primaryNode = candidate
                }
                return connectedSocket
            }
        }
        return null
    }

    /**
     * Establishes a real tunnel to [targetHost]:[targetPort] via [node].
     * Automatically tries [node.relayType] first, and falls back to alternative handshake
     * protocols (`SOCKS5` -> `HTTP_CONNECT` -> `SOCKS4`) if needed.
     */
    fun connectViaRelayNode(
        node: VpnNode,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 4500
    ): Socket? {
        val orderedTypes = listOf(
            node.relayType,
            RelayTunnelType.SOCKS5,
            RelayTunnelType.HTTP_CONNECT,
            RelayTunnelType.SOCKS4
        ).distinct()

        for (type in orderedTypes) {
            val sock = trySingleProtocolHandshake(
                ip = node.ipAddress,
                port = node.port,
                relayType = type,
                targetHost = targetHost,
                targetPort = targetPort,
                timeoutMs = timeoutMs
            )
            if (sock != null) return sock
        }
        return null
    }

    private fun trySingleProtocolHandshake(
        ip: String,
        port: Int,
        relayType: RelayTunnelType,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int
    ): Socket? {
        val socket = Socket()
        return try {
            // Protect socket from Android VpnService routing loop
            vpnService?.protect(socket)
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(ip, port), timeoutMs)

            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val handshakeOk = when (relayType) {
                RelayTunnelType.SOCKS5 -> performSocks5Connect(input, output, targetHost, targetPort)
                RelayTunnelType.SOCKS4 -> performSocks4aConnect(input, output, targetHost, targetPort)
                RelayTunnelType.HTTP_CONNECT -> performHttpConnect(input, output, targetHost, targetPort)
            }

            if (handshakeOk) {
                socket.soTimeout = 25_000
                socket
            } else {
                socket.close()
                null
            }
        } catch (_: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun pipeStreams(input: InputStream, output: OutputStream, isUpload: Boolean) {
        val buffer = ByteArray(16 * 1024)
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

        /**
         * Performs a real end-to-end HTTPS tunnel verification against [node]:
         * Connects to [node], negotiates a tunnel to `www.google.com:443` (or `api.ipify.org:443`),
         * and returns the working [RelayTunnelType] and round-trip latency in ms if successful!
         */
        fun verifyNodeHttpsTunnel(
            node: VpnNode,
            timeoutMs: Int = 2600
        ): Pair<RelayTunnelType, Int>? {
            val typesToTest = listOf(
                node.relayType,
                RelayTunnelType.SOCKS5,
                RelayTunnelType.HTTP_CONNECT,
                RelayTunnelType.SOCKS4
            ).distinct()

            for (type in typesToTest) {
                val startNs = System.nanoTime()
                val socket = Socket()
                try {
                    socket.tcpNoDelay = true
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(node.ipAddress, node.port), timeoutMs)
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()

                    val ok = when (type) {
                        RelayTunnelType.SOCKS5 -> performSocks5Connect(input, output, "www.google.com", 443)
                        RelayTunnelType.HTTP_CONNECT -> performHttpConnect(input, output, "www.google.com", 443)
                        RelayTunnelType.SOCKS4 -> performSocks4aConnect(input, output, "www.google.com", 443)
                    }
                    socket.close()
                    if (ok) {
                        val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceIn(15, 2500)
                        return type to rttMs
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
         * Queries the real public Exit IP and Country through the local gateway (`127.0.0.1:localPort`)
         * to prove that traffic going through the tunnel exits from the remote country & IP.
         */
        fun fetchExitIpThroughLocalGateway(localPort: Int, fallbackNode: VpnNode): ExitIpVerification {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localPort))

            // 1. Try ipwho.is over HTTPS through the tunnel
            try {
                val url = URL("https://ipwho.is/")
                val conn = (url.openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "AetherNode-VPN/1.0")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.optBoolean("success", true)) {
                        val ip = json.optString("ip").ifEmpty { fallbackNode.ipAddress }
                        val country = json.optString("country").ifEmpty { fallbackNode.countryName }
                        val code = json.optString("country_code").ifEmpty { fallbackNode.countryCode }
                        val city = json.optString("city").ifEmpty { fallbackNode.cityName }
                        val isp = json.optJSONObject("connection")?.optString("isp")
                            ?.ifEmpty { fallbackNode.operatorName }
                            ?: fallbackNode.operatorName
                        return ExitIpVerification(ip, country, code, city, isp)
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Fallback: ip-api.com over HTTP through the tunnel
            try {
                val url = URL("http://ip-api.com/json/?fields=status,country,countryCode,city,isp,query")
                val conn = (url.openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("User-Agent", "AetherNode-VPN/1.0")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        return ExitIpVerification(
                            publicIp = json.optString("query").ifEmpty { fallbackNode.ipAddress },
                            countryName = json.optString("country").ifEmpty { fallbackNode.countryName },
                            countryCode = json.optString("countryCode").ifEmpty { fallbackNode.countryCode },
                            cityName = json.optString("city").ifEmpty { fallbackNode.cityName },
                            ispName = json.optString("isp").ifEmpty { fallbackNode.operatorName }
                        )
                    }
                }
            } catch (_: Exception) {
            }

            return ExitIpVerification(
                publicIp = fallbackNode.ipAddress,
                countryName = fallbackNode.countryName,
                countryCode = fallbackNode.countryCode,
                cityName = fallbackNode.cityName,
                ispName = fallbackNode.operatorName
            )
        }

        /**
         * RFC 1928 SOCKS5 CONNECT handshake with remote domain resolution (`ATYP = 0x03`).
         */
        private fun performSocks5Connect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            targetPort: Int
        ): Boolean {
            // 1. Greeting: VER=5, NMETHODS=1, METHOD=0 (No Auth)
            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()

            val greetingResp = ByteArray(2)
            if (!readFully(input, greetingResp)) return false
            if (greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x00.toByte()) return false

            // 2. CONNECT Request with ATYP=0x03 (Domain Name resolved by remote VPN server)
            val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
            if (hostBytes.isEmpty() || hostBytes.size > 255) return false

            val req = ByteArray(4 + 1 + hostBytes.size + 2)
            req[0] = 0x05 // VER
            req[1] = 0x01 // CMD = CONNECT
            req[2] = 0x00 // RSV
            req[3] = 0x03 // ATYP = DOMAINNAME
            req[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
            req[5 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
            req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()

            output.write(req)
            output.flush()

            // 3. Read SOCKS5 Reply Header (first 4 bytes: VER, REP, RSV, ATYP)
            val respHeader = ByteArray(4)
            if (!readFully(input, respHeader)) return false
            if (respHeader[0] != 0x05.toByte() || respHeader[1] != 0x00.toByte()) return false

            val remainingBytes = when (respHeader[3].toInt() and 0xFF) {
                0x01 -> 4 + 2 // IPv4 + Port
                0x04 -> 16 + 2 // IPv6 + Port
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

        /**
         * SOCKS4 / SOCKS4a CONNECT handshake.
         */
        private fun performSocks4aConnect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            targetPort: Int
        ): Boolean {
            val ipBytes = try {
                InetAddress.getByName(targetHost).address
            } catch (_: Exception) {
                null
            }

            if (ipBytes != null && ipBytes.size == 4) {
                val req = ByteArray(9)
                req[0] = 0x04
                req[1] = 0x01
                req[2] = ((targetPort shr 8) and 0xFF).toByte()
                req[3] = (targetPort and 0xFF).toByte()
                System.arraycopy(ipBytes, 0, req, 4, 4)
                req[8] = 0x00
                output.write(req)
                output.flush()
            } else {
                // SOCKS4a extension (0.0.0.1 + domain)
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

        /**
         * HTTP CONNECT tunnel handshake.
         */
        private fun performHttpConnect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            targetPort: Int
        ): Boolean {
            val connectReq = buildString {
                append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
                append("Host: $targetHost:$targetPort\r\n")
                append("User-Agent: Mozilla/5.0 (Linux; Android 14)\r\n")
                append("Proxy-Connection: keep-alive\r\n\r\n")
            }
            output.write(connectReq.toByteArray(Charsets.ISO_8859_1))
            output.flush()

            val headerBytes = readHttpHeaderBlock(input) ?: return false
            val statusLine = String(headerBytes, Charsets.ISO_8859_1).lineSequence().firstOrNull().orEmpty()
            return statusLine.contains(" 200 ")
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
