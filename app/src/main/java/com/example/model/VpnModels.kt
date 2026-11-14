package com.example.model

/**
 * Geographic region filter categories for worldwide VPN exit servers.
 */
enum class VpnRegion(val displayName: String) {
    ALL("All Countries"),
    FAVORITES("Starred"),
    AMERICAS("Americas"),
    EUROPE("Europe"),
    ASIA_PACIFIC("Asia-Pacific"),
    MIDDLE_EAST_AFRICA("Global")
}

/**
 * Underlying wire protocol used by [com.example.vpn.LocalTunnelGateway] and
 * [com.example.vpn.TunPacketEngine] to tunnel traffic through the remote SSH / WireGuard exit server.
 */
enum class RelayTunnelType(val badgeLabel: String) {
    SSH_TUNNEL("SSH-2 High-BW"),
    WIREGUARD_TUNNEL("WireGuard ⚡"),
    SOCKS5("SSH Dynamic"),
    SOCKS4("SSH Stream"),
    HTTP_CONNECT("SSH TLS 443")
}

/**
 * Tunnel mode label shown in preferences and telemetry.
 */
enum class VpnProtocol(
    val shortName: String,
    val fullName: String,
    val cipherDescription: String,
    val defaultPort: Int
) {
    WIREGUARD_CHACHA20(
        shortName = "SSH + WireGuard",
        fullName = "Auto-Provisioned 4-Day SSH & WireGuard Tunnel",
        cipherDescription = "256KB High-Bandwidth Stream • Auto 4-Day Account • TUN0",
        defaultPort = 443
    ),
    OPENVPN_UDP_AES256(
        shortName = "SSH Dynamic",
        fullName = "SSH-2 Dynamic Port Forwarding",
        cipherDescription = "Full-Bandwidth SSH Multiplexed Stream",
        defaultPort = 1080
    ),
    OPENVPN_TCP_TLS(
        shortName = "SSH TLS 443",
        fullName = "SSH over TLS Port 443",
        cipherDescription = "High-Bandwidth TLS 443 Passthrough",
        defaultPort = 443
    )
}

/**
 * Sorting options for the country server list including explicit Ping Asc ↑ and Ping Desc ↓.
 */
enum class NodeSortOption(val label: String) {
    PING_ASC("Ping: Asc ↑"),
    PING_DESC("Ping: Desc ↓"),
    HIGHEST_SPEED("Speed ⚡"),
    COUNTRY_NAME("Country A–Z")
}

/**
 * Represents a high-bandwidth VPN server node with automatic 3-to-5-day ephemeral SSH/WireGuard account credentials.
 */
data class VpnNode(
    val id: String,
    val hostName: String,
    val ipAddress: String,
    val port: Int,
    val countryName: String,
    val countryCode: String,
    val cityName: String,
    val flagEmoji: String,
    val region: VpnRegion,
    val pingMs: Int,
    val downloadSpeedMbps: Double,
    val operatorName: String,
    val protocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val relayType: RelayTunnelType = RelayTunnelType.SOCKS5,
    val username: String = "",
    val password: String = "",
    val verifiedExitIp: String = "",
    val isLiveFromPublicApi: Boolean = false,
    val isVerifiedWorking: Boolean = false,
    val isCustomNode: Boolean = false,
    val accountExpiresAtEpochMs: Long = 0L,
    val wireguardPublicKey: String = "",
    val provisionedAccountId: String = ""
) {
    val signalBars: Int
        get() = when {
            pingMs <= 0 -> 2
            pingMs < 110 -> 4
            pingMs < 200 -> 3
            pingMs < 350 -> 2
            else -> 1
        }

    fun hasValidEphemeralAccount(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return username.isNotBlank() && accountExpiresAtEpochMs > nowEpochMs
    }

    fun remainingValidityLabel(nowEpochMs: Long = System.currentTimeMillis()): String {
        val remainingMs = accountExpiresAtEpochMs - nowEpochMs
        if (remainingMs <= 0L) return "Auto-Fetch 4d SSH/WG"
        val totalHours = (remainingMs / (1000L * 60L * 60L)).coerceAtLeast(1L)
        val days = totalHours / 24L
        val hours = totalHours % 24L
        return if (days > 0L) {
            "${days}d ${hours}h Free SSH/WG"
        } else {
            "${hours}h Free SSH/WG"
        }
    }
}

/**
 * Lifecycle state of the Android [android.net.VpnService] tunnel.
 */
enum class TunnelConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

/**
 * Result of verifying the real public Exit IP through the active tunnel.
 */
data class ExitIpVerification(
    val publicIp: String,
    val countryName: String,
    val countryCode: String,
    val cityName: String,
    val ispName: String
)

/**
 * Severity level for [VpnDiagnosticEntry] captured by [com.example.vpn.AetherVpnService].
 */
enum class VpnDiagnosticSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Structured handshake, tunnel, or connection error entry captured from [com.example.vpn.AetherVpnService].
 */
data class VpnDiagnosticEntry(
    val id: Long = System.nanoTime(),
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val phase: String,
    val serverLabel: String,
    val summary: String,
    val detail: String,
    val severity: VpnDiagnosticSeverity
)

/**
 * Live telemetry emitted by [com.example.vpn.AetherVpnService] while the TUN interface is active.
 */
data class TunnelTelemetry(
    val status: TunnelConnectionStatus = TunnelConnectionStatus.DISCONNECTED,
    val activeNode: VpnNode? = null,
    val connectedSinceEpochMs: Long = 0L,
    val durationSeconds: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val bytesUploaded: Long = 0L,
    val downloadRateKbps: Double = 0.0,
    val uploadRateKbps: Double = 0.0,
    val packetsProcessed: Long = 0L,
    val virtualIpAddress: String = "10.8.0.2",
    val deviceDirectIp: String? = null,
    val verifiedExitIp: String? = null,
    val verifiedExitCountry: String? = null,
    val verifiedExitCity: String? = null,
    val verifiedExitIsp: String? = null,
    val localGatewayPort: Int = 0,
    val activeDnsServer: String = "1.1.1.1",
    val activeMtu: Int = 1400,
    val activeProtocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val isForegroundServiceActive: Boolean = false,
    val handshakeSummary: String = "Ready to Connect",
    val errorMessage: String? = null,
    val lastFailureStage: String? = null,
    val lastFailureReason: String? = null,
    val lastFailedServerLabel: String? = null,
    val diagnosticLogs: List<VpnDiagnosticEntry> = emptyList()
)

/**
 * Lightweight user preferences stored via Jetpack DataStore.
 */
data class VpnPreferences(
    val selectedNodeId: String = "auto_fastest",
    val autoSelectFastest: Boolean = true,
    val favoriteNodeIds: Set<String> = setOf("US", "DE", "JP", "SG"),
    val preferredProtocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val dnsServer: String = "1.1.1.1",
    val isDarkTheme: Boolean = false,
    val wifiOnlyLatencyCheck: Boolean = false,
    val autoConnectOnStartup: Boolean = false,
    val lastSelectedNode: VpnNode? = null
)
