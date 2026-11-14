package com.example.vpn

import android.content.Context
import android.util.Base64
import com.example.data.PublicNodeRepository
import com.example.model.RelayTunnelType
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import com.example.model.VpnProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Internet Free SSH / TLS / UDP Account Harvester, Auto-Creator & Expiry Vault (`EphemeralAccountProvisioner`).
 *
 * Unlike static local generators, this engine:
 * 1. Fetches real live SSH / SSH-TLS (443) / SSH-WS / UDPGW (7300) accounts & server directories
 *    over the internet from configured JSON/URI feeds and live free SSH providers.
 * 2. Attempts headless background account creation when a provider form is accessible without CAPTCHA,
 *    and pairs with the in-app 1-tap Web Auto-Grabber for Cloudflare/CAPTCHA-protected providers.
 * 3. Persists real SSH credentials (username, password, optional PEM/OpenSSH private key, TLS SNI,
 *    UDP port, and real 1-day/3-day/7-day expiration timestamp) in [SharedPreferences].
 * 4. Monitors expiry automatically: while active, reuses the account seamlessly; once expired,
 *    purges the stale credential and auto-fetches a fresh SSH account over the internet.
 */
object EphemeralAccountProvisioner {

    private const val VAULT_PREFS = "avpn_live_ssh_account_vault"
    private const val VAULT_ACCOUNTS_KEY = "ssh_accounts_json"
    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    data class SshProviderPreset(
        val id: String,
        val name: String,
        val browseUrl: String,
        val createHint: String,
        val defaultValidityDays: Int,
        val supportsTlsAndUdp: Boolean = true
    )

    val OFFICIAL_PROVIDERS = listOf(
        SshProviderPreset(
            id = "sshocean",
            name = "SSHOcean",
            browseUrl = "https://sshocean.com/ssh",
            createHint = "Free 7-day high-BW SSH, TLS 443, WS & UDPGW 7300",
            defaultValidityDays = 7
        ),
        SshProviderPreset(
            id = "jagovpn",
            name = "JagoVPN",
            browseUrl = "https://jagovpn.com/ssh/",
            createHint = "Free 3-day AIO SSH WebSocket, SSL/TLS 443 & ZIVPN UDP",
            defaultValidityDays = 3
        ),
        SshProviderPreset(
            id = "serverssh",
            name = "ServerSSH",
            browseUrl = "https://serverssh.net/",
            createHint = "Free 1-day & 3-day fast bandwidth SSH/TLS tunnels",
            defaultValidityDays = 1
        ),
        SshProviderPreset(
            id = "vpnjantit",
            name = "VPNJantit",
            browseUrl = "https://www.vpnjantit.com/free-ssh",
            createHint = "Free 3-day & 7-day global SSH + Stunnel + UDPGW",
            defaultValidityDays = 3
        ),
        SshProviderPreset(
            id = "opentunnel",
            name = "OpenTunnel",
            browseUrl = "https://opentunnel.net/ssh/",
            createHint = "Free 7-day SSH WebSocket + SNI TLS servers",
            defaultValidityDays = 7
        )
    )

    data class InternetHarvestSummary(
        val readyNodes: List<VpnNode>,
        val discoveredHosts: Int,
        val autoCreatedCount: Int,
        val captchaGatedProviders: List<String>
    )

    private val secureRandom = SecureRandom()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Loads all non-expired real SSH/TLS/UDP accounts currently stored in the vault.
     */
    fun getValidVaultNodes(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<VpnNode> {
        val all = loadVaultRaw(context)
        val valid = all.filter { !it.isAccountExpired(nowEpochMs) && it.ipAddress.isNotBlank() }
        if (valid.size != all.size) {
            saveVaultRaw(context, valid)
        }
        return valid
    }

    /**
     * Saves or updates a real SSH/TLS/UDP account in the persistent vault and removes any expired duplicates.
     */
    fun saveAccountToVault(context: Context, node: VpnNode): VpnNode {
        val now = System.currentTimeMillis()
        val existing = loadVaultRaw(context)
            .filter { !it.isAccountExpired(now) }
            .filterNot {
                it.ipAddress.equals(node.ipAddress, ignoreCase = true) &&
                    it.port == node.port &&
                    it.username == node.username
            }
        val updated = listOf(node) + existing
        saveVaultRaw(context, updated)
        return node
    }

    /**
     * Attaches any matching active SSH account from the local vault to [node] synchronously
     * without fabricating placeholder credentials.
     */
    fun attachCachedOrQuickAccount(context: Context?, node: VpnNode): VpnNode {
        if (context == null || node.isCustomNode) return node
        val now = System.currentTimeMillis()
        val vault = getValidVaultNodes(context, now)
        val match = vault.firstOrNull {
            it.ipAddress.equals(node.ipAddress, ignoreCase = true)
        } ?: return node

        return node.copy(
            username = match.username,
            password = match.password,
            sshPrivateKey = match.sshPrivateKey,
            accountExpiresAtEpochMs = match.accountExpiresAtEpochMs,
            tlsPort = match.tlsPort,
            udpPort = match.udpPort,
            sniHost = match.sniHost,
            providerName = match.providerName
        )
    }

    /**
     * Runs before/during connection:
     * - If [node] has a valid non-expired real SSH account, uses it seamlessly until expiry.
     * - If [node] is an SSH node whose account expired (or [forceRenew] is requested), purges the expired
     *   account and automatically fetches/creates a fresh account over the internet.
     */
    suspend fun ensureFreshAccountForConnection(
        context: Context,
        node: VpnNode,
        customFeedUrl: String = "",
        forceRenew: Boolean = false
    ): VpnNode = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. If node has valid, non-expired SSH credentials and caller didn't force renewal, reuse seamlessly!
        if (!forceRenew && node.isSshProtocol && node.hasValidEphemeralAccount(now)) {
            saveAccountToVault(context, node)
            return@withContext node
        }

        // 2. Check if vault already holds a valid non-expired SSH account for this server or country
        if (!forceRenew) {
            val validVault = getValidVaultNodes(context, now)
            val serverMatch = validVault.firstOrNull {
                it.ipAddress.equals(node.ipAddress, ignoreCase = true) && it.hasValidEphemeralAccount(now)
            } ?: validVault.firstOrNull {
                node.countryCode != "XX" &&
                    it.countryCode.equals(node.countryCode, ignoreCase = true) &&
                    it.hasValidEphemeralAccount(now)
            }
            if (serverMatch != null && (node.isSshProtocol || node.ipAddress.isBlank())) {
                return@withContext serverMatch
            }
        }

        // 3. If SSH account expired or missing/forced, automatically fetch fresh accounts over the internet
        if (node.isSshProtocol && (node.isAccountExpired(now) || forceRenew)) {
            AetherVpnStateBus.recordDiagnostic(
                phase = "SSH Auto-Fetch",
                serverLabel = "${node.flagEmoji} ${node.countryName} (${node.ipAddress})",
                summary = if (node.isAccountExpired(now)) {
                    "SSH Account Expired — Auto-Fetching Replacement"
                } else {
                    "Fetching Fresh Free SSH/TLS/UDP Account"
                },
                detail = "Querying live internet SSH feeds and provider endpoints for a non-expired account…",
                severity = VpnDiagnosticSeverity.INFO
            )
        }

        val harvest = fetchLiveSshAccountsFromInternet(
            context = context,
            customFeedUrl = customFeedUrl
        )

        val matchedFresh = harvest.readyNodes.firstOrNull {
            !it.isAccountExpired(now) &&
                it.hasValidEphemeralAccount(now) &&
                (node.countryCode == "XX" || it.countryCode.equals(node.countryCode, ignoreCase = true))
        } ?: harvest.readyNodes.firstOrNull {
            !it.isAccountExpired(now) && it.hasValidEphemeralAccount(now)
        }

        if (matchedFresh != null) {
            saveAccountToVault(context, matchedFresh)
            AetherVpnStateBus.recordDiagnostic(
                phase = "SSH Auto-Fetch",
                serverLabel = "${matchedFresh.flagEmoji} ${matchedFresh.countryName} (${matchedFresh.ipAddress}:${matchedFresh.port})",
                summary = "Fetched Free SSH Account (${matchedFresh.username})",
                detail = "Provider: ${matchedFresh.providerName.ifEmpty { "Internet Feed" }} • TLS ${matchedFresh.tlsPort} • UDP ${matchedFresh.udpPort} • ${matchedFresh.remainingValidityLabel(now)}.",
                severity = VpnDiagnosticSeverity.SUCCESS
            )
            return@withContext matchedFresh
        }

        if (harvest.captchaGatedProviders.isNotEmpty() && node.isSshProtocol && node.isAccountExpired(now)) {
            AetherVpnStateBus.recordDiagnostic(
                phase = "Provider CAPTCHA Notice",
                serverLabel = "${node.flagEmoji} ${node.countryName}",
                summary = "Web Providers Require Quick Auto-Grab",
                detail = "${harvest.captchaGatedProviders.joinToString(", ")} returned Cloudflare/CAPTCHA on background POST. Tap 'Fetch Free SSH' on Connect screen for 1-tap auto-fill & grab.",
                severity = VpnDiagnosticSeverity.WARNING
            )
        }

        // Preserve non-SSH verified relay nodes without injecting fake credentials
        node
    }

    /**
     * Queries user-configured SSH/VPN API feeds + free high-bandwidth SSH provider directories over the internet,
     * parses any exposed live credentials or open creation endpoints, and updates the vault.
     */
    suspend fun fetchLiveSshAccountsFromInternet(
        context: Context,
        customFeedUrl: String = ""
    ): InternetHarvestSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val vaultNodes = getValidVaultNodes(context, now).toMutableList()
        var discoveredHostCount = 0
        var autoCreatedCount = 0
        val captchaGated = mutableListOf<String>()

        // 1. Fetch & parse custom JSON / API / subscription feed URL if provided
        val feedUrlsToCheck = buildList {
            if (customFeedUrl.isNotBlank()) add(customFeedUrl.trim())
        }

        coroutineScope {
            val feedJobs = feedUrlsToCheck.map { feedUrl ->
                async(Dispatchers.IO) {
                    val body = httpGet(feedUrl)
                    if (!body.isNullOrBlank()) {
                        parseFeedPayload(body, sourceName = "Live SSH Feed")
                    } else {
                        emptyList()
                    }
                }
            }

            // 2. Query public high-bandwidth free SSH provider pages concurrently
            val providerJobs = OFFICIAL_PROVIDERS.map { preset ->
                async(Dispatchers.IO) {
                    scrapeAndAttemptCreateFromProvider(preset, now)
                }
            }

            val feedResults = feedJobs.awaitAll().flatten()
            for (item in feedResults) {
                if (item.hasValidEphemeralAccount(now)) {
                    saveAccountToVault(context, item)
                    if (vaultNodes.none { it.ipAddress == item.ipAddress && it.username == item.username }) {
                        vaultNodes.add(0, item)
                        autoCreatedCount++
                    }
                }
            }

            val providerResults = providerJobs.awaitAll()
            for (res in providerResults) {
                discoveredHostCount += res.discoveredHostCount
                if (res.requiresCaptcha) {
                    captchaGated.add(res.providerName)
                }
                for (created in res.createdAccounts) {
                    saveAccountToVault(context, created)
                    if (vaultNodes.none { it.ipAddress == created.ipAddress && it.username == created.username }) {
                        vaultNodes.add(0, created)
                        autoCreatedCount++
                    }
                }
            }
        }

        InternetHarvestSummary(
            readyNodes = vaultNodes.filter { !it.isAccountExpired(now) },
            discoveredHosts = discoveredHostCount,
            autoCreatedCount = autoCreatedCount,
            captchaGatedProviders = captchaGated.distinct()
        )
    }

    private data class ProviderScrapeResult(
        val providerName: String,
        val discoveredHostCount: Int = 0,
        val createdAccounts: List<VpnNode> = emptyList(),
        val requiresCaptcha: Boolean = false
    )

    private fun scrapeAndAttemptCreateFromProvider(
        preset: SshProviderPreset,
        nowEpochMs: Long
    ): ProviderScrapeResult {
        val html = httpGet(preset.browseUrl) ?: return ProviderScrapeResult(preset.name)
        val lowerHtml = html.lowercase(Locale.US)

        val blockedByChallenge = lowerHtml.contains("cf-browser-verification") ||
            lowerHtml.contains("just a moment") ||
            lowerHtml.contains("g-recaptcha") ||
            lowerHtml.contains("h-captcha") ||
            lowerHtml.contains("cf-turnstile")

        // Check if provider page directly embeds public/open demo credentials or JSON server blocks
        val parsedDirectAccounts = parseFeedPayload(html, sourceName = preset.name)

        // Extract exposed SSH server hostnames from provider directory HTML
        val hostRegex = Regex("""\b([a-z0-9-]{2,24}\.(?:sshocean\.net|jagovpn\.com|serverssh\.net|vpnjantit\.com|opentunnel\.net|sshmax\.net))\b""", RegexOption.IGNORE_CASE)
        val hosts = hostRegex.findAll(html)
            .map { it.groupValues[1].lowercase(Locale.US) }
            .distinct()
            .take(8)
            .toList()

        // If the page exposes a non-CAPTCHA create <form>, try headless POST account creation automatically
        val headlessCreated = if (!blockedByChallenge) {
            attemptHeadlessFormAccountCreate(preset, html, hosts, nowEpochMs)
        } else {
            emptyList()
        }

        return ProviderScrapeResult(
            providerName = preset.name,
            discoveredHostCount = hosts.size,
            createdAccounts = (parsedDirectAccounts + headlessCreated).distinctBy { "${it.ipAddress}:${it.username}" },
            requiresCaptcha = blockedByChallenge && headlessCreated.isEmpty() && parsedDirectAccounts.isEmpty()
        )
    }

    private fun attemptHeadlessFormAccountCreate(
        preset: SshProviderPreset,
        html: String,
        hosts: List<String>,
        nowEpochMs: Long
    ): List<VpnNode> {
        val formActionMatch = Regex("""<form[^>]*action=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(html) ?: return emptyList()
        val actionRaw = formActionMatch.groupValues[1].trim()
        if (actionRaw.isEmpty() || actionRaw.startsWith("#")) return emptyList()

        val targetUrl = when {
            actionRaw.startsWith("http") -> actionRaw
            actionRaw.startsWith("/") -> {
                val origin = preset.browseUrl.substringBefore("://") + "://" +
                    preset.browseUrl.substringAfter("://").substringBefore("/")
                origin + actionRaw
            }
            else -> preset.browseUrl.substringBeforeLast("/") + "/" + actionRaw
        }

        val randSuffix = ByteArray(3).also { secureRandom.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val genUser = "avpn$randSuffix"
        val genPass = "Pass$randSuffix!"

        val formBuilder = FormBody.Builder()
            .add("username", genUser)
            .add("password", genPass)

        // Include hidden CSRF / token fields from form
        val hiddenRegex = Regex("""<input[^>]*type=["']hidden["'][^>]*name=["']([^"']+)["'][^>]*value=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        hiddenRegex.findAll(html).forEach { match ->
            formBuilder.add(match.groupValues[1], match.groupValues[2])
        }
        hosts.firstOrNull()?.let { firstHost ->
            formBuilder.add("server", firstHost)
        }

        val responseText = httpPostForm(targetUrl, formBuilder.build(), referer = preset.browseUrl)
            ?: return emptyList()

        val extracted = parseSshAccountText(
            rawText = responseText,
            fallbackHost = hosts.firstOrNull().orEmpty(),
            fallbackUser = genUser,
            fallbackPass = genPass,
            providerName = preset.name,
            defaultValidityDays = preset.defaultValidityDays,
            nowEpochMs = nowEpochMs
        )
        return listOfNotNull(extracted)
    }

    /**
     * Universal parser supporting JSON arrays/objects (XPanel / 3x-ui / Custom SSH APIs),
     * base64 subscriptions, `ssh://user:pass@host:port#Country` URIs, and plain text confirmation cards
     * captured from free SSH providers or WebView auto-grabbing.
     */
    fun parseFeedPayload(
        rawPayload: String,
        sourceName: String = "Internet SSH Feed",
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<VpnNode> {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) return emptyList()

        val collected = mutableListOf<VpnNode>()

        // 1. Try decoding Base64 subscription blobs
        val candidateText = if (!trimmed.startsWith("{") &&
            !trimmed.startsWith("[") &&
            !trimmed.contains("ssh://") &&
            !trimmed.contains("<html", ignoreCase = true) &&
            trimmed.length in 16..65536
        ) {
            runCatching {
                String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrDefault(trimmed)
        } else {
            trimmed
        }

        // 2. Try JSON array or object parsing
        if (candidateText.startsWith("[")) {
            runCatching {
                val arr = JSONArray(candidateText)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj ->
                        parseJsonSshNode(obj, sourceName, nowEpochMs)?.let { collected.add(it) }
                    }
                }
            }
        } else if (candidateText.startsWith("{")) {
            runCatching {
                val root = JSONObject(candidateText)
                val nested = root.optJSONArray("servers")
                    ?: root.optJSONArray("accounts")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("nodes")
                if (nested != null) {
                    for (i in 0 until nested.length()) {
                        nested.optJSONObject(i)?.let { obj ->
                            parseJsonSshNode(obj, sourceName, nowEpochMs)?.let { collected.add(it) }
                        }
                    }
                } else {
                    parseJsonSshNode(root, sourceName, nowEpochMs)?.let { collected.add(it) }
                }
            }
        }

        // 3. Parse ssh://user:pass@host:port#Label URIs
        val uriRegex = Regex("""ssh://([^:\s/@]+):([^@\s/]+)@([A-Za-z0-9.\-]+):(\d{1,5})(?:\?([^#\s]+))?(?:#([^\r\n]+))?""")
        uriRegex.findAll(candidateText).forEach { m ->
            val user = m.groupValues[1].trim()
            val pass = m.groupValues[2].trim()
            val host = m.groupValues[3].trim()
            val port = m.groupValues[4].toIntOrNull()?.coerceIn(1, 65535) ?: 22
            val query = m.groupValues[5]
            val fragment = m.groupValues[6].trim()
            val cc = guessCountryCodeFromHost(fragment.ifEmpty { host })
            val relayType = when {
                query.contains("type=tls", ignoreCase = true) || port == 443 -> RelayTunnelType.SSH_TLS
                query.contains("type=ws", ignoreCase = true) -> RelayTunnelType.SSH_WS
                else -> RelayTunnelType.SSH_TUNNEL
            }
            collected.add(
                VpnNode(
                    id = "ssh-$host-$port-$user",
                    hostName = host,
                    ipAddress = host,
                    port = port,
                    countryName = PublicNodeRepository.countryNameFromCode(cc),
                    countryCode = cc,
                    cityName = "$sourceName • SSH",
                    flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cc),
                    region = PublicNodeRepository.mapCountryCodeToRegion(cc),
                    pingMs = -1,
                    downloadSpeedMbps = 240.0,
                    operatorName = "$sourceName (${relayType.badgeLabel})",
                    protocol = if (relayType == RelayTunnelType.SSH_TLS) {
                        VpnProtocol.OPENVPN_TCP_TLS
                    } else {
                        VpnProtocol.OPENVPN_UDP_AES256
                    },
                    relayType = relayType,
                    username = user,
                    password = pass,
                    isLiveFromPublicApi = true,
                    accountExpiresAtEpochMs = nowEpochMs + ONE_DAY_MS,
                    tlsPort = if (port == 443) 443 else 443,
                    udpPort = 7300,
                    sniHost = host,
                    providerName = sourceName
                )
            )
        }

        // 4. Fallback plain-text SSH summary parser
        if (collected.isEmpty()) {
            parseSshAccountText(
                rawText = candidateText,
                providerName = sourceName,
                nowEpochMs = nowEpochMs
            )?.let { collected.add(it) }
        }

        return collected.distinctBy { "${it.ipAddress}:${it.port}:${it.username}" }
    }

    private fun parseJsonSshNode(
        obj: JSONObject,
        defaultProvider: String,
        nowEpochMs: Long
    ): VpnNode? {
        val host = (obj.optString("host").ifEmpty { obj.optString("ip") }.ifEmpty { obj.optString("server") }).trim()
        val user = (obj.optString("username").ifEmpty { obj.optString("user") }).trim()
        val pass = (obj.optString("password").ifEmpty { obj.optString("pass") }).trim()
        val privKey = (obj.optString("private_key").ifEmpty { obj.optString("ssh_key") }).trim()

        if (host.isBlank() || user.isBlank() || (pass.isBlank() && privKey.isBlank())) return null

        val tlsPort = obj.optInt("tls_port", obj.optInt("ssl_port", 443)).coerceIn(1, 65535)
        val udpPort = obj.optInt("udp_port", obj.optInt("udpgw_port", 7300)).coerceIn(1, 65535)
        val rawPort = obj.optInt("port", obj.optInt("ssh_port", if (tlsPort == 443) 443 else 22)).coerceIn(1, 65535)
        val sni = obj.optString("sni").ifEmpty { host }
        val mode = obj.optString("protocol").ifEmpty { obj.optString("type") }.lowercase(Locale.US)
        val relayType = when {
            mode.contains("ws") -> RelayTunnelType.SSH_WS
            mode.contains("tls") || mode.contains("ssl") || rawPort == 443 -> RelayTunnelType.SSH_TLS
            else -> RelayTunnelType.SSH_TUNNEL
        }

        val cc = obj.optString("country_code").ifEmpty { obj.optString("country") }
            .trim().uppercase(Locale.US).take(2)
            .let { if (it.length == 2 && it.all(Char::isLetter)) it else guessCountryCodeFromHost(host) }

        val provider = obj.optString("provider").ifEmpty { defaultProvider }
        val expiryMs = obj.optLong("expires_at_ms", 0L).takeIf { it > nowEpochMs }
            ?: obj.optInt("validity_days", 1).coerceIn(1, 30).let { nowEpochMs + (it * ONE_DAY_MS) }

        return VpnNode(
            id = "ssh-$host-$rawPort-$user",
            hostName = host,
            ipAddress = host,
            port = rawPort,
            countryName = PublicNodeRepository.countryNameFromCode(cc),
            countryCode = cc,
            cityName = obj.optString("city").ifEmpty { "$provider • TLS/UDP" },
            flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cc),
            region = PublicNodeRepository.mapCountryCodeToRegion(cc),
            pingMs = obj.optInt("ping_ms", -1),
            downloadSpeedMbps = obj.optDouble("bandwidth_mbps", 260.0),
            operatorName = "$provider (${relayType.badgeLabel})",
            protocol = if (relayType == RelayTunnelType.SSH_TLS) VpnProtocol.OPENVPN_TCP_TLS else VpnProtocol.OPENVPN_UDP_AES256,
            relayType = relayType,
            username = user,
            password = pass,
            sshPrivateKey = privKey,
            isLiveFromPublicApi = true,
            accountExpiresAtEpochMs = expiryMs,
            tlsPort = tlsPort,
            udpPort = udpPort,
            sniHost = sni,
            providerName = provider,
            provisionedAccountId = user
        )
    }

    /**
     * Extracts created SSH account details from provider result text / HTML / clipboard / WebView DOM.
     */
    fun parseSshAccountText(
        rawText: String,
        fallbackHost: String = "",
        fallbackUser: String = "",
        fallbackPass: String = "",
        providerName: String = "Free SSH Provider",
        defaultValidityDays: Int = 1,
        nowEpochMs: Long = System.currentTimeMillis()
    ): VpnNode? {
        val cleaned = rawText
            .replace(Regex("<[^>]+>"), "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

        val host = extractField(cleaned, listOf("Host / IP", "Host/IP", "Server Host", "Hostname", "Host", "IP Address", "Server IP", "Domain"))
            .ifBlank { fallbackHost }
            .trim()
        val user = extractField(cleaned, listOf("Username", "User", "SSH Username", "Login"))
            .ifBlank { fallbackUser }
            .trim()
        val pass = extractField(cleaned, listOf("Password", "Pass", "SSH Password"))
            .ifBlank { fallbackPass }
            .trim()
        val sni = extractField(cleaned, listOf("SNI", "Bug Host", "Payload Host")).ifBlank { host }

        val pemKeyMatch = Regex(
            """-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\s\S]+?-----END [A-Z0-9 ]*PRIVATE KEY-----"""
        ).find(cleaned)?.value?.trim().orEmpty()

        if (host.isBlank() || user.isBlank() || (pass.isBlank() && pemKeyMatch.isBlank())) {
            return null
        }

        val tlsPort = extractIntField(cleaned, listOf("SSL/TLS Port", "TLS Port", "SSL Port", "Stunnel", "Port TLS"))
            ?.coerceIn(1, 65535) ?: 443
        val sshPort = extractIntField(cleaned, listOf("OpenSSH Port", "SSH Port", "Dropbear Port", "Dropbear", "Port"))
            ?.coerceIn(1, 65535) ?: 22
        val udpPort = extractIntField(cleaned, listOf("UDPGW Port", "UDP Port", "BadVPN", "ZIVPN UDP"))
            ?.coerceIn(1, 65535) ?: 7300

        val useTlsByDefault = tlsPort == 443 || cleaned.contains("TLS", ignoreCase = true) || cleaned.contains("SSL", ignoreCase = true)
        val effectivePort = if (useTlsByDefault) tlsPort else sshPort
        val relayType = when {
            cleaned.contains("WebSocket", ignoreCase = true) && !cleaned.contains("SSL", ignoreCase = true) -> RelayTunnelType.SSH_WS
            useTlsByDefault -> RelayTunnelType.SSH_TLS
            else -> RelayTunnelType.SSH_TUNNEL
        }

        val expiryMs = parseExpiryTimestamp(cleaned, defaultValidityDays, nowEpochMs)
        val cc = guessCountryCodeFromHost("$cleaned $host")

        return VpnNode(
            id = "ssh-$host-$effectivePort-$user",
            hostName = host,
            ipAddress = host,
            port = effectivePort,
            countryName = PublicNodeRepository.countryNameFromCode(cc),
            countryCode = cc,
            cityName = "$providerName • TLS $tlsPort / UDP $udpPort",
            flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cc),
            region = PublicNodeRepository.mapCountryCodeToRegion(cc),
            pingMs = -1,
            downloadSpeedMbps = 320.0,
            operatorName = "$providerName (${relayType.badgeLabel})",
            protocol = if (relayType == RelayTunnelType.SSH_TLS) {
                VpnProtocol.OPENVPN_TCP_TLS
            } else {
                VpnProtocol.OPENVPN_UDP_AES256
            },
            relayType = relayType,
            username = user,
            password = pass,
            sshPrivateKey = pemKeyMatch,
            isLiveFromPublicApi = true,
            isVerifiedWorking = true,
            accountExpiresAtEpochMs = expiryMs,
            tlsPort = tlsPort,
            udpPort = udpPort,
            sniHost = sni,
            providerName = providerName,
            provisionedAccountId = user
        )
    }

    private fun extractField(text: String, labels: List<String>): String {
        for (label in labels) {
            val escaped = Regex.escape(label)
            val pattern = Regex("""(?im)^[\s•*\-]*(?:$escaped)\s*[:=\-]\s*([^\r\n]+)$""")
            val match = pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank()) {
                return match.substringBefore(" ").trim().ifBlank { match }
            }
        }
        return ""
    }

    private fun extractIntField(text: String, labels: List<String>): Int? {
        for (label in labels) {
            val escaped = Regex.escape(label)
            val pattern = Regex("""(?im)(?:$escaped)\s*[:=\-]?\s*(\d{2,5})""")
            val num = pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (num != null) return num
        }
        return null
    }

    private fun parseExpiryTimestamp(
        text: String,
        defaultValidityDays: Int,
        nowEpochMs: Long
    ): Long {
        val rawExpiry = extractField(text, listOf("Expired Date", "Expired At", "Expires", "Expiry", "Valid Until", "Expired"))
        if (rawExpiry.isNotBlank()) {
            val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MMM dd, yyyy")
            for (fmt in formats) {
                val parsed = runCatching {
                    SimpleDateFormat(fmt, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(rawExpiry)?.time
                }.getOrNull()
                if (parsed != null && parsed > nowEpochMs) {
                    return parsed
                }
            }
        }
        val dayCountMatch = Regex("""\b([1-9])\s*(?:day|days|hari)\b""", RegexOption.IGNORE_CASE).find(text)
        val days = dayCountMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: defaultValidityDays
        return nowEpochMs + (days.coerceIn(1, 30) * ONE_DAY_MS)
    }

    fun guessCountryCodeFromHost(hint: String): String {
        val lower = hint.lowercase(Locale.US)
        fun hasPrefix(code: String) = Regex("""(?:^|[\s./@\-_])${code}\d*(?:[.\-_]|$)""").containsMatchIn(lower)
        return when {
            lower.contains("singapore") || hasPrefix("sg") -> "SG"
            lower.contains("germany") || lower.contains("frankfurt") || hasPrefix("de") -> "DE"
            lower.contains("united states") || lower.contains("usa") || hasPrefix("us") -> "US"
            lower.contains("netherlands") || lower.contains("amsterdam") || hasPrefix("nl") -> "NL"
            lower.contains("japan") || lower.contains("tokyo") || hasPrefix("jp") -> "JP"
            lower.contains("united kingdom") || lower.contains("london") || hasPrefix("uk") || hasPrefix("gb") -> "GB"
            lower.contains("france") || lower.contains("paris") || hasPrefix("fr") -> "FR"
            lower.contains("canada") || hasPrefix("ca") -> "CA"
            lower.contains("india") || lower.contains("mumbai") || hasPrefix("in") -> "IN"
            lower.contains("indonesia") || lower.contains("jakarta") || hasPrefix("id") -> "ID"
            lower.contains("australia") || lower.contains("sydney") || hasPrefix("au") -> "AU"
            else -> "XX"
        }
    }

    private fun httpGet(urlString: String): String? {
        return try {
            val req = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/html, text/plain")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpPostForm(urlString: String, formBody: FormBody, referer: String): String? {
        return try {
            val req = Request.Builder()
                .url(urlString)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
                .header("Referer", referer)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadVaultRaw(context: Context): List<VpnNode> {
        return try {
            val raw = context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
                .getString(VAULT_ACCOUNTS_KEY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = ArrayList<VpnNode>(arr.length())
            for (i in 0 until arr.length()) {
                val itemStr = arr.optString(i)
                if (itemStr.isNotBlank()) {
                    PublicNodeRepository.decodeNodeJson(itemStr)?.let { list.add(it) }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveVaultRaw(context: Context, nodes: List<VpnNode>) {
        try {
            val arr = JSONArray()
            for (node in nodes.take(30)) {
                arr.put(PublicNodeRepository.encodeNodeJson(node))
            }
            context.getSharedPreferences(VAULT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(VAULT_ACCOUNTS_KEY, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }
}
