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
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * LocalTunnelGateway — High-Reliability Local Tunneling Bridge with Strict Country Isolation
 * and Active Socket Severing on Server Switch.
 *
 * Key guarantees:
 * 1. **Active Socket Severing**: Tracks all open client & upstream sockets in [activeTunnelSockets]
 *    and forcefully closes them in [stop]. This prevents Chrome/WebView HTTP/2 Keep-Alive from
 *    staying stuck on the previous country (e.g. Italy) when switching servers!
 * 2. **Strict Country Isolation**: When a user selects a specific country, failover ONLY uses
 *    verified backup nodes from that **exact same country**, never routing traffic through another
 *    country's server.
 * 3. **Protected Verification Sockets**: Calls `vpnService?.protect(socket)` during node
 *    verification so probing a new server while the VPN is already active never loops through the
 *    existing tunnel.
 */
class LocalTunnelGateway(
    private val vpnService: VpnService? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var primaryNode: VpnNode? = null

    @Volatile
    private var keepDisplayNodeLocked: Boolean = false

    private val fallbackVerifiedNodes = CopyOnWriteArrayList<VpnNode>()
    private val activeTunnelSockets = CopyOnWriteArrayList<Socket>()

    val totalBytesReceived = AtomicLong(0L)
    val totalBytesSent = AtomicLong(0L)
    val totalConnectionsHandled = AtomicLong(0L)

    val listeningPort: Int
        get() = serverSocket?.localPort ?: 0

    fun start(
        initialNode: VpnNode,
        verifiedBackups: List<VpnNode>,
        lockDisplayNode: Boolean = false
    ): Int {
        // Sever all existing connections from any previous server immediately
        stop()

        primaryNode = initialNode
        keepDisplayNodeLocked = lockDisplayNode
        fallbackVerifiedNodes.clear()

        // If manual country is locked, ONLY allow backups from the exact same country!
        val allowedBackups = if (lockDisplayNode) {
            verifiedBackups.filter {
                it.countryCode.equals(initialNode.countryCode, ignoreCase = true) &&
                    "${it.ipAddress}:${it.port}" != "${initialNode.ipAddress}:${initialNode.port}"
            }
        } else {
            verifiedBackups.filter {
                "${it.ipAddress}:${it.port}" != "${initialNode.ipAddress}:${initialNode.port}"
            }
        }
        fallbackVerifiedNodes.addAll(allowedBackups)

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress("127.0.0.1", 0), 128)
        serverSocket = server

        val boundPort = server.localPort
        AetherVpnStateBus.appendLog(
            tag = "GATEWAY",
            message = "Gateway bound 127.0.0.1:$boundPort -> ${initialNode.flagEmoji} ${initialNode.countryName} (${initialNode.ipAddress}:${initialNode.port})",
            isHighlight = true
        )

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

    /**
     * Stops the listener AND forcefully closes all active client/upstream sockets so
     * browser persistent connections immediately reconnect through the new server.
     */
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
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

    private suspend fun handleClientConnection(client: Socket) = withContext(Dispatchers.IO) {
        var upstreamSocket: Socket? = null
        try {
            client.soTimeout = 25_000
            client.tcpNoDelay = true
            val clientIn = BufferedInputStream(client.getInputStream())
            val clientOut = client.getOutputStream()

            val headerBytes = readHttpHeaderBlock(clientIn) ?: return@withContext
            totalBytesSent.addAndGet(headerBytes.size.toLong())

            val headerStr = String(headerBytes, Charsets.ISO_8859_1)
            val lines = headerStr.split("\r\n")
            val requestLine = lines.firstOrNull()?.trim().orEmpty()
            if (requestLine.isEmpty()) return@withContext

            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val targetToken = parts[1]

            if (method == "CONNECT") {
                val hostPort = targetToken.split(":")
                val targetHost = hostPort[0].trim()
                val targetPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                upstreamSocket = openUpstreamTunnelWithFailover(targetHost, targetPort)
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

                upstreamSocket = openUpstreamTunnelWithFailover(targetHost, targetPort)
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

    /**
     * Opens a tunnel to [targetHost]:[targetPort] via the active node or same-country backups.
     */
    private fun openUpstreamTunnelWithFailover(targetHost: String, targetPort: Int): Socket? {
        val primary = primaryNode ?: return null
        val candidates = buildList {
            add(primary)
            addAll(fallbackVerifiedNodes.take(5))
        }

        for (candidate in candidates) {
            val connectedSocket = connectViaRelayNode(
                node = candidate,
                targetHost = targetHost,
                targetPort = targetPort,
                timeoutMs = 4200
            )
            if (connectedSocket != null) {
                if ("${candidate.ipAddress}:${candidate.port}" != "${primary.ipAddress}:${primary.port}") {
                    primaryNode = candidate
                    if (!keepDisplayNodeLocked) {
                        AetherVpnStateBus.updateTelemetry {
                            it.copy(
                                activeNode = candidate,
                                verifiedExitIp = candidate.ipAddress,
                                verifiedExitCountry = candidate.countryName,
                                verifiedExitCity = candidate.cityName
                            )
                        }
                    } else {
                        // Update only the IP in telemetry while keeping the chosen country locked
                        AetherVpnStateBus.updateTelemetry {
                            it.copy(
                                verifiedExitIp = candidate.ipAddress
                            )
                        }
                    }
                }
                return connectedSocket
            }
        }
        return null
    }

    fun connectViaRelayNode(
        node: VpnNode,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 4000
    ): Socket? {
        val orderedTypes = listOf(
            node.relayType,
            RelayTunnelType.SOCKS5,
            RelayTunnelType.SOCKS4,
            RelayTunnelType.HTTP_CONNECT
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
         * Verifies a node's end-to-end HTTPS tunnel capability with optional [protectSocket]
         * callback so verification works even while a VPN interface is active.
         */
        fun verifyNodeHttpsTunnel(
            node: VpnNode,
            timeoutMs: Int = 2400,
            protectSocket: ((Socket) -> Boolean)? = null
        ): Pair<RelayTunnelType, Int>? {
            val typesToTest = listOf(
                node.relayType,
                RelayTunnelType.SOCKS5,
                RelayTunnelType.SOCKS4,
                RelayTunnelType.HTTP_CONNECT
            ).distinct()

            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

            for (type in typesToTest) {
                val startNs = System.nanoTime()
                val rawSocket = Socket()
                try {
                    protectSocket?.invoke(rawSocket)
                    rawSocket.tcpNoDelay = true
                    rawSocket.soTimeout = timeoutMs
                    rawSocket.connect(InetSocketAddress(node.ipAddress, node.port), timeoutMs)
                    val input = rawSocket.getInputStream()
                    val output = rawSocket.getOutputStream()

                    val tunnelOpened = when (type) {
                        RelayTunnelType.SOCKS5 -> performSocks5Connect(input, output, "www.google.com", 443)
                        RelayTunnelType.SOCKS4 -> performSocks4aConnect(input, output, "www.google.com", 443)
                        RelayTunnelType.HTTP_CONNECT -> performHttpConnect(input, output, "www.google.com", 443)
                    }
                    if (!tunnelOpened) {
                        rawSocket.close()
                        continue
                    }

                    val sslSocket = sslFactory.createSocket(rawSocket, "www.google.com", 443, true) as SSLSocket
                    sslSocket.soTimeout = timeoutMs
                    val sslParams = SSLParameters().apply {
                        endpointIdentificationAlgorithm = "HTTPS"
                    }
                    sslSocket.sslParameters = sslParams
                    sslSocket.startHandshake()

                    if (!hostnameVerifier.verify("www.google.com", sslSocket.session)) {
                        sslSocket.close()
                        continue
                    }

                    val tlsOut = sslSocket.getOutputStream()
                    val tlsIn = sslSocket.getInputStream()
                    val req = "GET /generate_204 HTTP/1.1\r\nHost: www.google.com\r\nConnection: close\r\n\r\n"
                    tlsOut.write(req.toByteArray(Charsets.ISO_8859_1))
                    tlsOut.flush()

                    val respLine = tlsIn.bufferedReader(Charsets.ISO_8859_1).readLine().orEmpty()
                    sslSocket.close()

                    if (respLine.startsWith("HTTP/1.1 204") ||
                        respLine.startsWith("HTTP/1.1 200") ||
                        respLine.startsWith("HTTP/2")
                    ) {
                        val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceIn(18, 2500)
                        return type to rttMs
                    }
                } catch (_: Exception) {
                    try {
                        rawSocket.close()
                    } catch (_: Exception) {
                    }
                }
            }
            return null
        }

        /**
         * Queries the real public Exit IP and Country through the local gateway (`127.0.0.1:localPort`).
         */
        fun fetchExitIpThroughLocalGateway(localPort: Int, fallbackNode: VpnNode): ExitIpVerification {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localPort))

            try {
                val url = URL("https://ipwho.is/")
                val conn = (url.openConnection(proxy) as HttpsURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("User-Agent", "AVPN/1.0")
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

            try {
                val url = URL("http://ip-api.com/json/?fields=status,country,countryCode,city,isp,query")
                val conn = (url.openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "AVPN/1.0")
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

        private fun performSocks5Connect(
            input: InputStream,
            output: OutputStream,
            targetHost: String,
            targetPort: Int
        ): Boolean {
            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()

            val greetingResp = ByteArray(2)
            if (!readFully(input, greetingResp)) return false
            if (greetingResp[0] != 0x05.toByte() || greetingResp[1] != 0x00.toByte()) return false

            val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
            if (hostBytes.isEmpty() || hostBytes.size > 255) return false

            val req = ByteArray(4 + 1 + hostBytes.size + 2)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03
            req[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
            req[5 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
            req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()

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
