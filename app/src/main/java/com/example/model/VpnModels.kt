package com.example.model

/**
 * Geographic region filter categories for worldwide VPN exit relays.
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
 * [com.example.vpn.TunPacketEngine] to tunnel traffic through the remote exit server.
 */
enum class RelayTunnelType(val badgeLabel: String) {
    SOCKS5("SOCKS5 TLS"),
    SOCKS4("SOCKS4a"),
    HTTP_CONNECT("HTTPS Tunnel")
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
        shortName = "Smart Auto",
        fullName = "TLS-Verified VpnService Tunnel",
        cipherDescription = "Full TUN0 Capture • Strict CA TLS Check • Remote Exit IP",
        defaultPort = 1080
    ),
    OPENVPN_UDP_AES256(
        shortName = "SOCKS5 Tunnel",
        fullName = "SOCKS5 Full-Route Stream",
        cipherDescription = "RFC 1928/1929 TUN0 Bridge",
        defaultPort = 1080
    ),
    OPENVPN_TCP_TLS(
        shortName = "HTTPS Tunnel",
        fullName = "HTTP CONNECT Port 443",
        cipherDescription = "TLS Passthrough via Port 443",
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
 * Represents a strictly TLS-verified VPN relay node or user-configured Pro/Custom server.
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
    val isCustomNode: Boolean = false
) {
    val signalBars: Int
        get() = when {
            pingMs <= 0 -> 2
            pingMs < 110 -> 4
            pingMs < 200 -> 3
            pingMs < 350 -> 2
            else -> 1
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
