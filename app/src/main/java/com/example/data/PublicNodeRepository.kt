package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.RelayTunnelType
import com.example.model.VpnNode
import com.example.model.VpnPreferences
import com.example.model.VpnProtocol
import com.example.model.VpnRegion
import com.example.vpn.AetherVpnStateBus
import com.example.vpn.LocalTunnelGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val Context.vpnDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aethernode_preferences"
)

/**
 * Live Multi-Source VPN Relay Repository for **A VPN** (By Ashir ♥️).
 *
 * Guarantees:
 * - Zero fake servers and zero fabricated/identical pings.
 * - Fetches live SOCKS5 & HTTP CONNECT relays from 6 independent worldwide feeds in parallel.
 * - Stage 1 filters candidates using raw `System.nanoTime()` TCP handshakes (`measureRealTcpPingMs`).
 * - Stage 2 strictly runs end-to-end HTTPS TLS + `api.ipify.org` Exit-IP verification
 *   (`LocalTunnelGateway.verifyNodeEndToEndTls`), rejecting any proxy that fails CA validation
 *   or fails to change the user's real public IP.
 * - Streams verified relays incrementally via `onWaveVerified` so the UI populates live.
 */
class PublicNodeRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    val preferencesFlow: Flow<VpnPreferences> = context.vpnDataStore.data.map { prefs ->
        val protocolName = prefs[KEY_PREFERRED_PROTOCOL] ?: VpnProtocol.WIREGUARD_CHACHA20.name
        val protocol = runCatching { VpnProtocol.valueOf(protocolName) }
            .getOrDefault(VpnProtocol.WIREGUARD_CHACHA20)

        VpnPreferences(
            selectedNodeId = prefs[KEY_SELECTED_NODE_ID] ?: "auto_fastest",
            autoSelectFastest = prefs[KEY_AUTO_SELECT_FASTEST] ?: true,
            favoriteNodeIds = prefs[KEY_FAVORITE_NODES]
                ?: setOf("US", "DE", "JP", "SG"),
            preferredProtocol = protocol,
            dnsServer = prefs[KEY_DNS_SERVER] ?: "1.1.1.1",
            isDarkTheme = prefs[KEY_DARK_THEME] ?: false,
            wifiOnlyLatencyCheck = prefs[KEY_WIFI_ONLY_LATENCY] ?: false,
            autoConnectOnStartup = prefs[KEY_AUTO_CONNECT_ON_STARTUP] ?: false,
            lastSelectedNode = prefs[KEY_LAST_SELECTED_NODE_JSON]?.let { decodeNodeJson(it) },
            sshProviderFeedUrl = prefs[KEY_SSH_PROVIDER_FEED_URL] ?: ""
        )
    }

    suspend fun setSelectedNode(
        nodeId: String,
        isAutoFastest: Boolean = false,
        node: VpnNode? = null
    ) {
        context.vpnDataStore.edit {
            it[KEY_SELECTED_NODE_ID] = nodeId
            it[KEY_AUTO_SELECT_FASTEST] = isAutoFastest
            if (node != null && node.ipAddress.isNotBlank()) {
                it[KEY_LAST_SELECTED_NODE_JSON] = encodeNodeJson(node)
            }
        }
    }

    suspend fun saveLastSelectedNode(node: VpnNode) {
        if (node.ipAddress.isBlank() || node.port !in 1..65535) return
        context.vpnDataStore.edit {
            it[KEY_LAST_SELECTED_NODE_JSON] = encodeNodeJson(node)
        }
    }

    suspend fun toggleFavoriteCountry(countryCode: String) {
        context.vpnDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_NODES] ?: setOf("US", "DE", "JP", "SG")
            prefs[KEY_FAVORITE_NODES] = if (current.contains(countryCode)) {
                current - countryCode
            } else {
                current + countryCode
            }
        }
    }

    suspend fun updateDarkTheme(isDark: Boolean) {
        context.vpnDataStore.edit { it[KEY_DARK_THEME] = isDark }
    }

    suspend fun updateWifiOnlyLatencyCheck(wifiOnly: Boolean) {
        context.vpnDataStore.edit { it[KEY_WIFI_ONLY_LATENCY] = wifiOnly }
    }

    suspend fun updateAutoConnectOnStartup(enabled: Boolean) {
        context.vpnDataStore.edit { it[KEY_AUTO_CONNECT_ON_STARTUP] = enabled }
    }

    suspend fun updateSshProviderFeedUrl(feedUrl: String) {
        context.vpnDataStore.edit { it[KEY_SSH_PROVIDER_FEED_URL] = feedUrl.trim() }
    }

    /**
     * Neutral bootstrap placeholder before the first live scan finishes.
     * Holds no fake IP or fake ping.
     */
    fun getInitialBootstrapNode(): VpnNode {
        val cachedSsh = com.example.vpn.EphemeralAccountProvisioner.getValidVaultNodes(context).firstOrNull()
        if (cachedSsh != null) return cachedSsh

        return VpnNode(
            id = "auto_scanning",
            hostName = "",
            ipAddress = "",
            port = 443,
            countryName = "Auto SSH / TLS / UDP",
            countryCode = "XX",
            cityName = "Fetching live internet SSH & VPN servers…",
            flagEmoji = "⚡",
            region = VpnRegion.ALL,
            pingMs = -1,
            downloadSpeedMbps = 220.0,
            operatorName = "Live Internet Feed",
            protocol = VpnProtocol.WIREGUARD_CHACHA20,
            relayType = RelayTunnelType.SSH_TLS,
            isVerifiedWorking = false
        )
    }

    /**
     * Discovers and strictly TLS + Exit-IP verifies live worldwide relays in 2 parallel stages,
     * automatically binding a 4-day high-bandwidth SSH/WireGuard account to every verified server.
     */
    suspend fun fetchLivePublicNodes(
        deviceDirectIp: String? = null,
        sshFeedUrl: String = "",
        onWaveVerified: ((List<VpnNode>) -> Unit)? = null
    ): Result<List<VpnNode>> = withContext(Dispatchers.IO) {
        try {
            val protectSocket = AetherVpnStateBus.protectSocketCallback
            val protectDatagram = AetherVpnStateBus.protectDatagramCallback

            val resolvedDirectIp = deviceDirectIp
                ?: AetherVpnStateBus.telemetry.value.deviceDirectIp
                ?: LocalTunnelGateway.fetchDeviceDirectIp(protectSocket)?.also { ip ->
                    AetherVpnStateBus.updateTelemetry { it.copy(deviceDirectIp = ip) }
                }

            val sshHarvestDeferred = async(Dispatchers.IO) {
                runCatching {
                    com.example.vpn.EphemeralAccountProvisioner.fetchLiveSshAccountsFromInternet(
                        context = context,
                        customFeedUrl = sshFeedUrl
                    )
                }.getOrNull()
            }

            val rawCandidates = fetchAllPublicFeedsInParallel()
            val sshHarvest = sshHarvestDeferred.await()
            val harvestedSshNodes = sshHarvest?.readyNodes.orEmpty()

            if (rawCandidates.isEmpty() && harvestedSshNodes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Unable to reach live server endpoints"))
            }

            // Pick diverse candidates across all countries, prioritizing harvested SSH accounts first
            val diverseCandidates = (harvestedSshNodes + pickDiverseCountryCandidates(rawCandidates, maxTotal = 160))
                .distinctBy { "${it.ipAddress}:${it.port}:${it.username}" }

            // Stage 1: Lightweight parallel TCP handshake duration filter
            val tcpSemaphore = Semaphore(16)
            val allReachable = coroutineScope {
                diverseCandidates.map { candidate ->
                    async(Dispatchers.IO) {
                        tcpSemaphore.withPermit {
                            val rtt = LocalTunnelGateway.measureTcpHandshakeDurationMs(
                                ipOrHost = candidate.ipAddress,
                                port = candidate.port,
                                timeoutMs = 1800,
                                protectSocket = protectSocket
                            )
                            if (rtt != null) candidate.copy(pingMs = rtt) else null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (allReachable.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            AetherVpnStateBus.updateCandidateBackupPool(allReachable)

            // Interleave up to 6 fastest candidates PER country so Singapore never crowds out US/EU/JP
            val stage2Candidates = selectBalancedPerCountryForTls(allReachable, perCountryCap = 6, maxTotal = 96)

            // Stage 2: Strict End-to-End HTTPS TLS + multi-endpoint Exit-IP verification (3000ms timeout)
            val tlsSemaphore = Semaphore(16)
            val verifiedCollector = Collections.synchronizedList(mutableListOf<VpnNode>())

            coroutineScope {
                stage2Candidates.map { candidate ->
                    async(Dispatchers.IO) {
                        tlsSemaphore.withPermit {
                            val res = LocalTunnelGateway.verifyNodeEndToEndTls(
                                node = candidate,
                                deviceDirectIp = resolvedDirectIp,
                                timeoutMs = 3000,
                                protectSocket = protectSocket,
                                protectDatagram = protectDatagram
                            )
                            if (res != null) {
                                val verifiedBase = candidate.copy(
                                    relayType = res.relayType,
                                    pingMs = res.realTcpPingMs,
                                    verifiedExitIp = res.verifiedExitIp,
                                    isVerifiedWorking = true,
                                    isLiveFromPublicApi = true
                                )
                                val geoVerified = verifyTrueExitGeolocation(verifiedBase)
                                val enriched = com.example.vpn.EphemeralAccountProvisioner.attachCachedOrQuickAccount(
                                    context = context,
                                    node = geoVerified
                                )
                                val (allVerifiedSnapshot, dedupSnapshot) = synchronized(verifiedCollector) {
                                    verifiedCollector.add(enriched)
                                    val fullCopy = verifiedCollector
                                        .sortedWith(
                                            compareByDescending<VpnNode> { it.isSshProtocol }
                                                .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                                        )
                                    val dedupCopy = deduplicateFastestPerCountry(fullCopy)
                                    fullCopy to dedupCopy
                                }
                                AetherVpnStateBus.updateVerifiedBackupPool(allVerifiedSnapshot)
                                AetherVpnStateBus.updateNodePool(dedupSnapshot)
                                onWaveVerified?.invoke(dedupSnapshot)
                            }
                        }
                    }
                }.awaitAll()
            }

            val fullVerified = synchronized(verifiedCollector) {
                verifiedCollector.sortedWith(
                    compareByDescending<VpnNode> { it.isSshProtocol }
                        .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                )
            }
            val finalVerified = deduplicateFastestPerCountry(fullVerified)
            AetherVpnStateBus.updateVerifiedBackupPool(fullVerified)
            AetherVpnStateBus.updateNodePool(finalVerified)
            onWaveVerified?.invoke(finalVerified)
            Result.success(finalVerified)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAllPublicFeedsInParallel(): List<VpnNode> = coroutineScope {
        val f1 = async(Dispatchers.IO) { runCatching { fetchProxyScrapeV4() }.getOrDefault(emptyList()) }
        val f2 = async(Dispatchers.IO) { runCatching { fetchProxiflyJson("https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/socks5/data.json", RelayTunnelType.SOCKS5) }.getOrDefault(emptyList()) }
        val f3 = async(Dispatchers.IO) { runCatching { fetchProxiflyJson("https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/http/data.json", RelayTunnelType.HTTP_CONNECT) }.getOrDefault(emptyList()) }
        val f4 = async(Dispatchers.IO) { runCatching { fetchGeoNode() }.getOrDefault(emptyList()) }
        val f5 = async(Dispatchers.IO) { runCatching { fetchPlainHostPortList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt", RelayTunnelType.SOCKS5, "Monosans SOCKS5") }.getOrDefault(emptyList()) }
        val f6 = async(Dispatchers.IO) { runCatching { fetchPlainHostPortList("https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt", RelayTunnelType.SOCKS5, "SpeedX SOCKS5") }.getOrDefault(emptyList()) }
        val f7 = async(Dispatchers.IO) { runCatching { fetchPlainHostPortList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt", RelayTunnelType.HTTP_CONNECT, "Monosans HTTP") }.getOrDefault(emptyList()) }

        // Prioritize SOCKS5 feeds first when deduplicating IP:Port
        (f2.await() + f1.await() + f4.await() + f5.await() + f6.await() + f3.await() + f7.await())
            .distinctBy { "${it.ipAddress}:${it.port}" }
    }

    private fun fetchProxyScrapeV4(): List<VpnNode> {
        val url = "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&proxy_format=protocolipport&format=json&timeout=3000"
        val body = httpGetString(url) ?: return emptyList()
        val root = JSONObject(body)
        val proxies = root.optJSONArray("proxies") ?: return emptyList()
        val out = ArrayList<VpnNode>(proxies.length())
        for (i in 0 until minOf(proxies.length(), 150)) {
            val item = proxies.optJSONObject(i) ?: continue
            val ip = item.optString("ip").trim()
            val port = item.optInt("port", 0)
            if (ip.isEmpty() || port !in 1..65535) continue
            val proto = item.optString("protocol").lowercase(Locale.US)
            val relayType = when {
                proto.contains("socks5") -> RelayTunnelType.SOCKS5
                proto.contains("socks4") -> RelayTunnelType.SOCKS4
                else -> RelayTunnelType.HTTP_CONNECT
            }
            val geo = item.optJSONObject("ip_data")
            val cc = geo?.optString("countryCode")?.take(2)?.uppercase(Locale.US).orEmpty().ifEmpty { "XX" }
            val country = geo?.optString("country").orEmpty().ifEmpty { countryNameFromCode(cc) }
            val city = geo?.optString("city").orEmpty().ifEmpty { "Relay" }
            val isp = geo?.optString("isp").orEmpty().ifEmpty { "ProxyScrape Live" }
            val speedKbps = item.optDouble("average_timeout", 800.0)
            out.add(
                buildCandidateNode(
                    ip = ip,
                    port = port,
                    countryCode = cc,
                    countryName = country,
                    cityName = city,
                    operatorName = isp,
                    relayType = relayType,
                    estMbps = if (speedKbps > 0) (12000.0 / speedKbps).coerceIn(10.0, 250.0) else 45.0
                )
            )
        }
        return out
    }

    private fun fetchProxiflyJson(url: String, defaultType: RelayTunnelType): List<VpnNode> {
        val body = httpGetString(url) ?: return emptyList()
        val arr = JSONArray(body)
        val out = ArrayList<VpnNode>(minOf(arr.length(), 150))
        for (i in 0 until minOf(arr.length(), 150)) {
            val item = arr.optJSONObject(i) ?: continue
            val ip = item.optString("ip").trim()
            val port = item.optInt("port", 0)
            if (ip.isEmpty() || port !in 1..65535) continue
            val geo = item.optJSONObject("geolocation")
            val cc = geo?.optString("country")?.take(2)?.uppercase(Locale.US).orEmpty().ifEmpty { "XX" }
            val city = geo?.optString("city").orEmpty().ifEmpty { "Relay" }
            out.add(
                buildCandidateNode(
                    ip = ip,
                    port = port,
                    countryCode = cc,
                    countryName = countryNameFromCode(cc),
                    cityName = city,
                    operatorName = "Proxifly Live",
                    relayType = defaultType,
                    estMbps = 65.0
                )
            )
        }
        return out
    }

    private fun fetchGeoNode(): List<VpnNode> {
        val url = "https://proxylist.geonode.com/api/proxy-list?limit=120&page=1&sort_by=lastChecked&sort_type=desc&protocols=socks5%2Chttp"
        val body = httpGetString(url) ?: return emptyList()
        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<VpnNode>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val ip = item.optString("ip").trim()
            val port = item.optString("port").toIntOrNull() ?: continue
            if (ip.isEmpty() || port !in 1..65535) continue
            val protocols = item.optJSONArray("protocols")
            val firstProto = protocols?.optString(0)?.lowercase(Locale.US).orEmpty()
            val relayType = when {
                firstProto.contains("socks5") -> RelayTunnelType.SOCKS5
                firstProto.contains("socks4") -> RelayTunnelType.SOCKS4
                else -> RelayTunnelType.HTTP_CONNECT
            }
            val cc = item.optString("country").take(2).uppercase(Locale.US).ifEmpty { "XX" }
            val city = item.optString("city").ifEmpty { "Relay" }
            val org = item.optString("org").ifEmpty { "GeoNode Live" }
            val speed = item.optDouble("speed", 40.0)
            out.add(
                buildCandidateNode(
                    ip = ip,
                    port = port,
                    countryCode = cc,
                    countryName = countryNameFromCode(cc),
                    cityName = city,
                    operatorName = org,
                    relayType = relayType,
                    estMbps = speed.coerceIn(10.0, 200.0)
                )
            )
        }
        return out
    }

    private fun fetchPlainHostPortList(
        url: String,
        relayType: RelayTunnelType,
        operatorLabel: String
    ): List<VpnNode> {
        val body = httpGetString(url) ?: return emptyList()
        val out = ArrayList<VpnNode>(80)
        for (rawLine in body.lineSequence()) {
            if (out.size >= 80) break
            val line = rawLine.trim()
            val colonIdx = line.lastIndexOf(':')
            if (colonIdx <= 0) continue
            val ip = line.substring(0, colonIdx).trim()
            val port = line.substring(colonIdx + 1).trim().toIntOrNull() ?: continue
            if (port !in 1..65535) continue
            out.add(
                buildCandidateNode(
                    ip = ip,
                    port = port,
                    countryCode = "XX",
                    countryName = "Global Relay",
                    cityName = ip,
                    operatorName = operatorLabel,
                    relayType = relayType,
                    estMbps = 50.0
                )
            )
        }
        return out
    }

    private data class ExitGeoInfo(
        val countryCode: String,
        val countryName: String,
        val cityName: String,
        val ispName: String
    )

    private val exitGeoCache = ConcurrentHashMap<String, ExitGeoInfo>()

    /**
     * Always resolves the TRUE country/city of the proxy's verified outbound Exit IP (`verifiedExitIp`),
     * because public feeds frequently label multi-hop/cloud relays with the inbound IP's country while
     * Google sees a completely different outbound country (e.g. feed says DE, exit IP is in GB).
     */
    private fun verifyTrueExitGeolocation(node: VpnNode): VpnNode {
        val lookupTarget = node.verifiedExitIp.ifEmpty { node.ipAddress }
        if (lookupTarget.isBlank()) return node

        val cached = exitGeoCache[lookupTarget]
        val resolved = cached ?: fetchGeoForExitIp(lookupTarget)?.also {
            exitGeoCache[lookupTarget] = it
        } ?: return node

        if (resolved.countryCode.length != 2 || resolved.countryCode == "XX") return node

        return node.copy(
            id = "country-${resolved.countryCode}",
            countryCode = resolved.countryCode,
            countryName = resolved.countryName.ifEmpty { countryNameFromCode(resolved.countryCode) },
            cityName = resolved.cityName.ifEmpty { node.cityName },
            flagEmoji = countryCodeToFlagEmoji(resolved.countryCode),
            region = mapCountryCodeToRegion(resolved.countryCode),
            operatorName = resolved.ispName.ifEmpty { node.operatorName }
        )
    }

    private fun fetchGeoForExitIp(ip: String): ExitGeoInfo? {
        // 1. Primary HTTPS GeoIP lookup (ipwho.is gives country, code, city, isp)
        try {
            val body = httpGetString("https://ipwho.is/$ip")
            if (!body.isNullOrBlank()) {
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) {
                    val cc = json.optString("country_code").take(2).uppercase(Locale.US)
                    if (cc.length == 2 && cc != "XX") {
                        return ExitGeoInfo(
                            countryCode = cc,
                            countryName = json.optString("country").ifEmpty { countryNameFromCode(cc) },
                            cityName = json.optString("city"),
                            ispName = json.optJSONObject("connection")?.optString("isp").orEmpty()
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback HTTPS GeoIP lookup (api.country.is is Cloudflare-backed with generous quota)
        try {
            val body = httpGetString("https://api.country.is/$ip")
            if (!body.isNullOrBlank()) {
                val json = JSONObject(body)
                val cc = json.optString("country").take(2).uppercase(Locale.US)
                if (cc.length == 2 && cc != "XX") {
                    return ExitGeoInfo(
                        countryCode = cc,
                        countryName = countryNameFromCode(cc),
                        cityName = "",
                        ispName = ""
                    )
                }
            }
        } catch (_: Exception) {
        }

        return null
    }

    private fun httpGetString(urlString: String): String? {
        return try {
            val req = Request.Builder()
                .url(urlString)
                .header("User-Agent", "AVPN-Android/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCandidateNode(
        ip: String,
        port: Int,
        countryCode: String,
        countryName: String,
        cityName: String,
        operatorName: String,
        relayType: RelayTunnelType,
        estMbps: Double
    ): VpnNode {
        val cc = countryCode.uppercase(Locale.US).ifEmpty { "XX" }
        return VpnNode(
            id = "country-$cc",
            hostName = ip,
            ipAddress = ip,
            port = port,
            countryName = countryName,
            countryCode = cc,
            cityName = cityName,
            flagEmoji = countryCodeToFlagEmoji(cc),
            region = mapCountryCodeToRegion(cc),
            pingMs = -1,
            downloadSpeedMbps = estMbps,
            operatorName = operatorName,
            protocol = VpnProtocol.WIREGUARD_CHACHA20,
            relayType = relayType,
            isLiveFromPublicApi = true,
            isVerifiedWorking = false
        )
    }

    /**
     * Selects diverse candidates across all countries, prioritizing SOCKS5 relays first within each country.
     */
    private fun pickDiverseCountryCandidates(nodes: List<VpnNode>, maxTotal: Int): List<VpnNode> {
        val byCountry = nodes.groupBy { it.countryCode.uppercase(Locale.US) }
            .mapValues { (_, list) ->
                list.sortedBy { if (it.relayType == RelayTunnelType.SOCKS5) 0 else 1 }
            }
        val result = ArrayList<VpnNode>(maxTotal)
        val roundLimit = 8
        val knownKeys = byCountry.keys.filter { it != "XX" }

        for (round in 0 until roundLimit) {
            for (cc in knownKeys) {
                val bucket = byCountry[cc] ?: continue
                if (round < bucket.size) {
                    result.add(bucket[round])
                    if (result.size >= maxTotal) return result
                }
            }
        }
        val unknownBucket = byCountry["XX"].orEmpty()
        for (candidate in unknownBucket) {
            if (result.size >= maxTotal) break
            result.add(candidate)
        }
        return result
    }

    /**
     * Selects up to [perCountryCap] fastest TCP-reachable candidates PER country in round-robin
     * so low-latency local hubs (like SG) never crowd out transcontinental servers (US, DE, GB, JP, etc.).
     */
    private fun selectBalancedPerCountryForTls(
        reachable: List<VpnNode>,
        perCountryCap: Int,
        maxTotal: Int
    ): List<VpnNode> {
        val byCountry = reachable.groupBy { it.countryCode.uppercase(Locale.US) }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareBy<VpnNode> { if (it.relayType == RelayTunnelType.SOCKS5) 0 else 1 }
                        .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                ).take(perCountryCap)
            }
        val result = ArrayList<VpnNode>(maxTotal)
        val knownKeys = byCountry.keys.filter { it != "XX" }

        for (round in 0 until perCountryCap) {
            for (cc in knownKeys) {
                val bucket = byCountry[cc] ?: continue
                if (round < bucket.size) {
                    result.add(bucket[round])
                    if (result.size >= maxTotal) return result
                }
            }
        }
        val unknownBucket = byCountry["XX"].orEmpty()
        for (candidate in unknownBucket) {
            if (result.size >= maxTotal) break
            result.add(candidate)
        }
        return result
    }

    fun deduplicateFastestPerCountry(nodes: List<VpnNode>): List<VpnNode> {
        return nodes
            .filter { it.ipAddress.isNotBlank() }
            .groupBy {
                if (it.isCustomNode) "CUSTOM_${it.ipAddress}_${it.port}"
                else it.countryCode.uppercase(Locale.US)
            }
            .map { (key, bucket) ->
                val best = bucket.sortedWith(
                    compareByDescending<VpnNode> { it.isCustomNode }
                        .thenByDescending { it.isVerifiedWorking }
                        .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                ).first()
                if (best.isCustomNode) best else best.copy(id = "country-${key.uppercase(Locale.US)}")
            }
            .sortedBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
    }

    companion object {
        private val KEY_SELECTED_NODE_ID = stringPreferencesKey("selected_node_id")
        private val KEY_AUTO_SELECT_FASTEST = booleanPreferencesKey("auto_select_fastest")
        private val KEY_FAVORITE_NODES = stringSetPreferencesKey("favorite_node_ids")
        private val KEY_PREFERRED_PROTOCOL = stringPreferencesKey("preferred_protocol")
        private val KEY_DNS_SERVER = stringPreferencesKey("dns_server")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_WIFI_ONLY_LATENCY = booleanPreferencesKey("wifi_only_latency_check")
        private val KEY_AUTO_CONNECT_ON_STARTUP = booleanPreferencesKey("auto_connect_on_startup")
        private val KEY_LAST_SELECTED_NODE_JSON = stringPreferencesKey("last_selected_node_json")
        private val KEY_SSH_PROVIDER_FEED_URL = stringPreferencesKey("ssh_provider_feed_url")

        fun encodeNodeJson(node: VpnNode): String {
            return JSONObject().apply {
                put("id", node.id)
                put("hostName", node.hostName)
                put("ipAddress", node.ipAddress)
                put("port", node.port)
                put("countryName", node.countryName)
                put("countryCode", node.countryCode)
                put("cityName", node.cityName)
                put("flagEmoji", node.flagEmoji)
                put("pingMs", node.pingMs)
                put("downloadSpeedMbps", node.downloadSpeedMbps)
                put("operatorName", node.operatorName)
                put("protocol", node.protocol.name)
                put("relayType", node.relayType.name)
                put("username", node.username)
                put("password", node.password)
                put("verifiedExitIp", node.verifiedExitIp)
                put("isLiveFromPublicApi", node.isLiveFromPublicApi)
                put("isVerifiedWorking", node.isVerifiedWorking)
                put("isCustomNode", node.isCustomNode)
                put("accountExpiresAtEpochMs", node.accountExpiresAtEpochMs)
                put("wireguardPublicKey", node.wireguardPublicKey)
                put("provisionedAccountId", node.provisionedAccountId)
                put("tlsPort", node.tlsPort)
                put("udpPort", node.udpPort)
                put("sniHost", node.sniHost)
                put("sshPrivateKey", node.sshPrivateKey)
                put("providerName", node.providerName)
            }.toString()
        }

        fun decodeNodeJson(rawJson: String): VpnNode? {
            if (rawJson.isBlank()) return null
            return runCatching {
                val obj = JSONObject(rawJson)
                val ip = obj.optString("ipAddress").trim()
                val port = obj.optInt("port", 0)
                if (ip.isBlank() || port !in 1..65535) return null
                val cc = obj.optString("countryCode", "XX").take(2).uppercase(Locale.US).ifEmpty { "XX" }
                val protocol = runCatching {
                    VpnProtocol.valueOf(obj.optString("protocol", VpnProtocol.WIREGUARD_CHACHA20.name))
                }.getOrDefault(VpnProtocol.WIREGUARD_CHACHA20)
                val relayType = runCatching {
                    RelayTunnelType.valueOf(obj.optString("relayType", RelayTunnelType.SOCKS5.name))
                }.getOrDefault(RelayTunnelType.SOCKS5)

                VpnNode(
                    id = obj.optString("id", "country-$cc"),
                    hostName = obj.optString("hostName", ip).ifEmpty { ip },
                    ipAddress = ip,
                    port = port,
                    countryName = obj.optString("countryName", countryNameFromCode(cc)),
                    countryCode = cc,
                    cityName = obj.optString("cityName", "Relay"),
                    flagEmoji = obj.optString("flagEmoji", countryCodeToFlagEmoji(cc)),
                    region = mapCountryCodeToRegion(cc),
                    pingMs = obj.optInt("pingMs", -1),
                    downloadSpeedMbps = obj.optDouble("downloadSpeedMbps", 180.0),
                    operatorName = obj.optString("operatorName", "Live SSH/TLS Server"),
                    protocol = protocol,
                    relayType = relayType,
                    username = obj.optString("username", ""),
                    password = obj.optString("password", ""),
                    verifiedExitIp = obj.optString("verifiedExitIp", ""),
                    isLiveFromPublicApi = obj.optBoolean("isLiveFromPublicApi", true),
                    isVerifiedWorking = obj.optBoolean("isVerifiedWorking", false),
                    isCustomNode = obj.optBoolean("isCustomNode", false),
                    accountExpiresAtEpochMs = obj.optLong("accountExpiresAtEpochMs", 0L),
                    wireguardPublicKey = obj.optString("wireguardPublicKey", ""),
                    provisionedAccountId = obj.optString("provisionedAccountId", ""),
                    tlsPort = obj.optInt("tlsPort", 443),
                    udpPort = obj.optInt("udpPort", 7300),
                    sniHost = obj.optString("sniHost", ""),
                    sshPrivateKey = obj.optString("sshPrivateKey", ""),
                    providerName = obj.optString("providerName", "")
                )
            }.getOrNull()
        }

        fun countryCodeToFlagEmoji(countryCode: String): String {
            val clean = countryCode.trim().uppercase(Locale.US)
            if (clean.length != 2 || clean == "XX" || !clean[0].isLetter() || !clean[1].isLetter()) {
                return "🌐"
            }
            val firstChar = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        }

        fun countryNameFromCode(countryCode: String): String {
            val clean = countryCode.trim().uppercase(Locale.US)
            if (clean.length != 2 || clean == "XX") return "Global Relay"
            val name = Locale("", clean).getDisplayCountry(Locale.ENGLISH)
            return if (name.isNullOrBlank()) clean else name
        }

        fun mapCountryCodeToRegion(countryCode: String): VpnRegion {
            return when (countryCode.uppercase(Locale.US)) {
                "US", "CA", "MX", "BR", "AR", "CL", "CO", "PE" -> VpnRegion.AMERICAS
                "DE", "CH", "NL", "GB", "UK", "FR", "SE", "NO", "FI", "IS", "IT", "ES", "PL", "RO", "CZ", "AT", "IE", "BE", "UA" -> VpnRegion.EUROPE
                "JP", "KR", "SG", "AU", "NZ", "IN", "TW", "HK", "TH", "VN", "MY", "ID", "PH" -> VpnRegion.ASIA_PACIFIC
                else -> VpnRegion.MIDDLE_EAST_AFRICA
            }
        }
    }
}
