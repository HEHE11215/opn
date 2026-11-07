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
        fullName = "WireGuard UDP Framing",
        cipherDescription = "ChaCha20-Poly1305 • Curve25519 • BLAKE2s",
        defaultPort = 51820
    ),
    OPENVPN_UDP_AES256(
        shortName = "OpenVPN UDP",
        fullName = "OpenVPN 2.6 UDP Tunnel",
        cipherDescription = "AES-256-GCM • TLS 1.3 Control Channel",
        defaultPort = 1194
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
 * Compatible with the public academic VPN Gate relay list and custom community nodes.
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
    val protocol: VpnProtocol = VpnProtocol.OPENVPN_TCP_TLS,
    val openVpnConfigPreview: String = "",
    val isLiveFromPublicApi: Boolean = false,
    val isCustomNode: Boolean = false
) {
    /**
     * Human-readable signal quality rating based on measured latency in milliseconds.
     */
    val latencyQualityLabel: String
        get() = when {
            pingMs <= 0 -> "Untested"
            pingMs < 65 -> "Optimal"
            pingMs < 135 -> "Good"
            pingMs < 220 -> "Fair"
            else -> "High Latency"
        }

    /**
     * Returns 1..4 signal bars based on latency.
     */
    val signalBars: Int
        get() = when {
            pingMs <= 0 -> 2
            pingMs < 65 -> 4
            pingMs < 135 -> 3
            pingMs < 220 -> 2
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
 */
data class VpnPreferences(
    val selectedNodeId: String = "jp-tokyo-academic-01",
    val favoriteNodeIds: Set<String> = setOf("jp-tokyo-academic-01", "de-frankfurt-open-01", "us-ny-relay-01"),
    val preferredProtocol: VpnProtocol = VpnProtocol.WIREGUARD_CHACHA20,
    val dnsServer: String = "1.1.1.1",
    val safeSplitTunnelMode: Boolean = true,
    val autoReconnect: Boolean = true,
    val isDarkTheme: Boolean = false
)
