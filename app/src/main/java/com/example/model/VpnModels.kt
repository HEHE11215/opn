package com.example.model

/**
 * Geographic region filter categories for worldwide free VPN relay nodes.
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
 * Underlying wire protocol used by the local tunnel gateway to forward traffic
 * through the remote public relay node.
 */
enum class RelayTunnelType(val badgeLabel: String) {
    SOCKS5("SOCKS5 TLS"),
    SOCKS4("SOCKS4a"),
    HTTP_CONNECT("HTTPS Tunnel")
}

/**
 * Supported open-source tunnel encapsulation and cipher suites.
 */
enum class VpnProtocol(
    val shortName: String,
    val fullName: String,
    val cipherDescription: String,
    val defaultPort: Int
) {
    WIREGUARD_CHACHA20(
        shortName = "Smart Auto",
        fullName = "Auto TLS-Verified Tunnel",
        cipherDescription = "Strict CA TLS Verification • Anti-MITM • Remote DNS",
        defaultPort = 1080
    ),
    OPENVPN_UDP_AES256(
        shortName = "SOCKS5 Fast",
        fullName = "SOCKS5 Direct Stream",
        cipherDescription = "Zero-Interception Raw TCP Stream",
        defaultPort = 1080
    ),
    OPENVPN_TCP_TLS(
        shortName = "Stealth 443",
        fullName = "TCP Port 443 Firewall Bypass",
        cipherDescription = "TLS 1.3 Verified Passthrough",
        defaultPort = 443
    )
}

/**
 * Clean consumer sorting options for the country server list.
 */
enum class NodeSortOption(val label: String) {
    LOWEST_PING("Fastest First"),
    COUNTRY_NAME("Country A–Z"),
    HIGHEST_SPEED("Top Speed")
}

/**
 * Represents the best auto-selected free VPN server for a specific country.
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
    val activeSessions: Int,
    val uptimeHours: Int,
    val operatorName: String,
    val protocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val relayType: RelayTunnelType = RelayTunnelType.SOCKS5,
    val openVpnConfigPreview: String = "",
    val isLiveFromPublicApi: Boolean = false,
    val isVerifiedWorking: Boolean = false,
    val isCustomNode: Boolean = false
) {
    /**
     * Consumer-friendly speed/quality rating based on latency.
     */
    val latencyQualityLabel: String
        get() = when {
            isVerifiedWorking && pingMs < 150 -> "Fast & Verified"
            isVerifiedWorking -> "Verified Online"
            pingMs < 90 -> "Fast"
            pingMs < 180 -> "Good"
            else -> "Standard"
        }

    /**
     * Returns 1..4 signal bars based on latency.
     */
    val signalBars: Int
        get() = when {
            pingMs <= 0 -> 3
            pingMs < 95 -> 4
            pingMs < 180 -> 3
            pingMs < 300 -> 2
            else -> 1
        }
}

/**
 * Lifecycle state of the Android VpnService encrypted tunnel.
 */
enum class TunnelConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

/**
 * Result of verifying the real public Exit IP through the active tunnel gateway.
 */
data class ExitIpVerification(
    val publicIp: String,
    val countryName: String,
    val countryCode: String,
    val cityName: String,
    val ispName: String
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
    val verifiedExitIp: String? = null,
    val verifiedExitCountry: String? = null,
    val verifiedExitCity: String? = null,
    val verifiedExitIsp: String? = null,
    val localGatewayPort: Int = 0,
    val activeDnsServer: String = "1.1.1.1",
    val activeMtu: Int = 1400,
    val activeProtocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val handshakeSummary: String = "Ready for Auto-Connect",
    val errorMessage: String? = null
)

/**
 * Open-source transparency audit log entry.
 */
data class AuditLogEntry(
    val id: Long,
    val timestampFormatted: String,
    val tag: String,
    val message: String,
    val isHighlight: Boolean = false
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
    val safeSplitTunnelMode: Boolean = false,
    val autoReconnect: Boolean = true,
    val isDarkTheme: Boolean = false
)
