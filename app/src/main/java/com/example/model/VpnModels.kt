package com.example.model

/**
 * Geographic region filter categories for worldwide free VPN relay nodes.
 */
enum class VpnRegion(val displayName: String) {
    ALL("All Worldwide"),
    FAVORITES("Starred"),
    AMERICAS("Americas"),
    EUROPE("Europe"),
    ASIA_PACIFIC("Asia-Pacific"),
    MIDDLE_EAST_AFRICA("MEA & Global")
}

/**
 * Underlying wire protocol used by the local tunnel gateway to forward traffic
 * through the remote public relay node.
 */
enum class RelayTunnelType(val badgeLabel: String) {
    SOCKS5("SOCKS5 TLS"),
    SOCKS4("SOCKS4a"),
    HTTP_CONNECT("HTTP CONNECT")
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
        shortName = "WireGuard-Mode",
        fullName = "WireGuard + SOCKS5/CONNECT Bridge",
        cipherDescription = "ChaCha20-Poly1305 • Remote DNS • Anti-QUIC Leak",
        defaultPort = 51820
    ),
    OPENVPN_UDP_AES256(
        shortName = "OpenVPN UDP",
        fullName = "OpenVPN 2.6 UDP Tunnel",
        cipherDescription = "AES-256-GCM • TLS 1.3 Control Channel",
        defaultPort = 1080
    ),
    OPENVPN_TCP_TLS(
        shortName = "OpenVPN TCP",
        fullName = "OpenVPN 2.6 TCP/443 Stealth",
        cipherDescription = "AES-256-GCM • TCP Port 443 Firewall Bypass",
        defaultPort = 443
    )
}

/**
 * Sorting options for the node browser.
 */
enum class NodeSortOption(val label: String) {
    LOWEST_PING("Lowest Ping"),
    HIGHEST_SPEED("Fastest Bandwidth"),
    UPTIME("Most Stable")
}

/**
 * Represents a public, free community VPN relay node anywhere in the world.
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
     * Human-readable signal quality rating based on measured latency in milliseconds.
     */
    val latencyQualityLabel: String
        get() = when {
            isVerifiedWorking && pingMs < 180 -> "Verified Live"
            pingMs <= 0 -> "Untested"
            pingMs < 85 -> "Optimal"
            pingMs < 165 -> "Good"
            pingMs < 280 -> "Fair"
            else -> "High Latency"
        }

    /**
     * Returns 1..4 signal bars based on latency.
     */
    val signalBars: Int
        get() = when {
            pingMs <= 0 -> 2
            pingMs < 85 -> 4
            pingMs < 165 -> 3
            pingMs < 280 -> 2
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
    val handshakeSummary: String = "Idle — No active TUN interface",
    val errorMessage: String? = null
)

/**
 * Open-source transparency audit log entry so contributors and users can inspect
 * every socket probe, TUN descriptor setup, DNS binding, and packet loop event.
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
 * Note: [safeSplitTunnelMode] defaults to `false` (Full Device Tunnel Mode) so that
 * Chrome, Google Search, and all browsers route traffic through the VPN gateway and change
 * the device's public IP and country.
 */
data class VpnPreferences(
    val selectedNodeId: String = "us-ny-relay-01",
    val favoriteNodeIds: Set<String> = setOf("us-ny-relay-01", "de-frankfurt-open-01", "jp-tokyo-academic-01"),
    val preferredProtocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val dnsServer: String = "1.1.1.1",
    val safeSplitTunnelMode: Boolean = false,
    val autoReconnect: Boolean = true,
    val isDarkTheme: Boolean = false
)
