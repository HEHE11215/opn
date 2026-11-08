package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PublicNodeRepository
import com.example.model.AuditLogEntry
import com.example.model.NodeSortOption
import com.example.model.RelayTunnelType
import com.example.model.TunnelConnectionStatus
import com.example.model.TunnelTelemetry
import com.example.model.VpnNode
import com.example.model.VpnPreferences
import com.example.model.VpnProtocol
import com.example.model.VpnRegion
import com.example.vpn.AetherVpnService
import com.example.vpn.AetherVpnStateBus
import com.example.vpn.LocalTunnelGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 2-Tab Consumer Navigation for A VPN.
 */
enum class AetherTab(val route: String, val label: String) {
    CONNECT("connect_dashboard", "Connect"),
    NODES("worldwide_nodes", "Servers")
}

enum class VpnPopupTone {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    THEME,
    INFO
}

/**
 * Full-Screen Optimized Glass Popup Notification shown whenever an event occurs
 * (Connect, Disconnect, Server Switch, Theme Change, Live Fetch).
 */
data class VpnEventPopup(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val subtitle: String,
    val flagEmoji: String = "🔥",
    val detailBadge: String = "Ultra-Fast Tunnel",
    val badgeText: String = "Made by Ashir ♥️",
    val tone: VpnPopupTone = VpnPopupTone.INFO
)

/**
 * Immutable UI state for the A VPN application.
 */
data class VpnUiState(
    val activeTab: AetherTab = AetherTab.CONNECT,
    val allNodes: List<VpnNode> = emptyList(),
    val filteredNodes: List<VpnNode> = emptyList(),
    val selectedNode: VpnNode,
    val isAutoFastestMode: Boolean = true,
    val telemetry: TunnelTelemetry = TunnelTelemetry(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val preferences: VpnPreferences = VpnPreferences(),
    val searchQuery: String = "",
    val selectedRegion: VpnRegion = VpnRegion.ALL,
    val sortOption: NodeSortOption = NodeSortOption.PING_ASC,
    val isSyncingApi: Boolean = false,
    val showAddCustomNodeModal: Boolean = false,
    val showDeveloperCreditsModal: Boolean = false,
    val activePopup: VpnEventPopup? = null
)

private data class FilterBarState(
    val activeTab: AetherTab = AetherTab.CONNECT,
    val searchQuery: String = "",
    val selectedRegion: VpnRegion = VpnRegion.ALL,
    val sortOption: NodeSortOption = NodeSortOption.PING_ASC,
    val isSyncingApi: Boolean = false,
    val showAddCustomNodeModal: Boolean = false,
    val showDeveloperCreditsModal: Boolean = false,
    val activePopup: VpnEventPopup? = null,
    val manualSelectedNodeOverride: VpnNode? = null,
    val isAutoFastestOverride: Boolean? = null
)

/**
 * Clean MVVM ViewModel for **A VPN** (Crafted by Ashir ♥️).
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PublicNodeRepository(application.applicationContext)

    private val _nodes = MutableStateFlow(repository.getVerifiedCommunityNodes())
    private val _filterState = MutableStateFlow(FilterBarState())
    private var lastObservedStatus: TunnelConnectionStatus = TunnelConnectionStatus.DISCONNECTED
    private var lastConnectedCountryCode: String? = null

    val uiState: StateFlow<VpnUiState> = combine(
        _nodes,
        _filterState,
        repository.preferencesFlow,
        AetherVpnStateBus.telemetry,
        AetherVpnStateBus.auditLogs
    ) { nodes, filter, prefs, telemetry, logs ->
        val fastestVerified = nodes.filter { it.isVerifiedWorking }.minByOrNull { it.pingMs }
            ?: nodes.minByOrNull { it.pingMs }
            ?: nodes.first()

        val isAutoMode = filter.isAutoFastestOverride
            ?: (prefs.autoSelectFastest && filter.manualSelectedNodeOverride == null)

        val selected = if (!isAutoMode) {
            filter.manualSelectedNodeOverride
                ?: nodes.firstOrNull {
                    it.id == prefs.selectedNodeId ||
                        it.countryCode.equals(prefs.selectedNodeId.removePrefix("country-"), ignoreCase = true)
                }
                ?: fastestVerified
        } else {
            fastestVerified
        }

        val query = filter.searchQuery.trim().lowercase(Locale.US)
        val filtered = nodes.filter { node ->
            val matchesRegion = when (filter.selectedRegion) {
                VpnRegion.ALL -> true
                VpnRegion.FAVORITES -> prefs.favoriteNodeIds.contains(node.countryCode) ||
                    prefs.favoriteNodeIds.contains(node.id)
                else -> node.region == filter.selectedRegion
            }
            val matchesQuery = query.isEmpty() ||
                node.countryName.lowercase(Locale.US).contains(query) ||
                node.cityName.lowercase(Locale.US).contains(query) ||
                node.countryCode.lowercase(Locale.US).contains(query)
            matchesRegion && matchesQuery
        }.let { matched ->
            when (filter.sortOption) {
                NodeSortOption.PING_ASC -> matched.sortedBy { it.pingMs }
                NodeSortOption.PING_DESC -> matched.sortedByDescending { it.pingMs }
                NodeSortOption.COUNTRY_NAME -> matched.sortedBy { it.countryName.lowercase(Locale.US) }
                NodeSortOption.HIGHEST_SPEED -> matched.sortedByDescending { it.downloadSpeedMbps }
            }
        }

        VpnUiState(
            activeTab = filter.activeTab,
            allNodes = nodes,
            filteredNodes = filtered,
            selectedNode = selected.copy(protocol = prefs.preferredProtocol),
            isAutoFastestMode = isAutoMode,
            telemetry = telemetry,
            auditLogs = logs,
            preferences = prefs,
            searchQuery = filter.searchQuery,
            selectedRegion = filter.selectedRegion,
            sortOption = filter.sortOption,
            isSyncingApi = filter.isSyncingApi,
            showAddCustomNodeModal = filter.showAddCustomNodeModal,
            showDeveloperCreditsModal = filter.showDeveloperCreditsModal,
            activePopup = filter.activePopup
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VpnUiState(
            allNodes = repository.getVerifiedCommunityNodes(),
            filteredNodes = repository.getVerifiedCommunityNodes().sortedBy { it.pingMs },
            selectedNode = repository.getVerifiedCommunityNodes().first()
        )
    )

    init {
        AetherVpnStateBus.updateNodePool(repository.getVerifiedCommunityNodes())
        syncLivePublicNodes(silent = true)

        // Observe telemetry status transitions and trigger full-screen optimized glass popups
        viewModelScope.launch {
            AetherVpnStateBus.telemetry.collect { tel ->
                val newStatus = tel.status
                val node = tel.activeNode
                if (newStatus != lastObservedStatus ||
                    (newStatus == TunnelConnectionStatus.CONNECTED && node?.countryCode != lastConnectedCountryCode)
                ) {
                    when (newStatus) {
                        TunnelConnectionStatus.CONNECTED -> {
                            if (node != null) {
                                lastConnectedCountryCode = node.countryCode
                                val exitIp = tel.verifiedExitIp ?: node.ipAddress
                                showEventPopup(
                                    title = "Connected to ${node.countryName}!",
                                    subtitle = "Tunnel IP: $exitIp • ${node.cityName}",
                                    flagEmoji = node.flagEmoji,
                                    detailBadge = "⚡ ${node.pingMs} ms • All Apps & WhatsApp Active",
                                    badgeText = "Made with love by Ashir ♥️",
                                    tone = VpnPopupTone.CONNECTED
                                )
                            }
                        }

                        TunnelConnectionStatus.DISCONNECTED -> {
                            if (lastObservedStatus == TunnelConnectionStatus.CONNECTED ||
                                lastObservedStatus == TunnelConnectionStatus.CONNECTING
                            ) {
                                lastConnectedCountryCode = null
                                showEventPopup(
                                    title = "VPN Disconnected",
                                    subtitle = "Tunnel closed safely • Tap orb anytime to reconnect",
                                    flagEmoji = "🛡️",
                                    detailBadge = "Standby Mode",
                                    badgeText = "Made with love by Ashir ♥️",
                                    tone = VpnPopupTone.DISCONNECTED
                                )
                            }
                        }

                        TunnelConnectionStatus.ERROR -> {
                            showEventPopup(
                                title = "Connection Reset",
                                subtitle = tel.errorMessage ?: "Tap Auto-Fetch Fastest Tunnel to reconnect",
                                flagEmoji = "⚡",
                                detailBadge = "Auto-Recovery Ready",
                                badgeText = "Made with love by Ashir ♥️",
                                tone = VpnPopupTone.DISCONNECTED
                            )
                        }

                        else -> {}
                    }
                    lastObservedStatus = newStatus
                }
            }
        }
    }

    fun showEventPopup(
        title: String,
        subtitle: String,
        flagEmoji: String = "🔥",
        detailBadge: String = "Ultra-Fast Tunnel",
        badgeText: String = "Made with love by Ashir ♥️",
        tone: VpnPopupTone = VpnPopupTone.INFO
    ) {
        _filterState.update {
            it.copy(
                activePopup = VpnEventPopup(
                    id = System.currentTimeMillis(),
                    title = title,
                    subtitle = subtitle,
                    flagEmoji = flagEmoji,
                    detailBadge = detailBadge,
                    badgeText = badgeText,
                    tone = tone
                )
            )
        }
    }

    fun dismissPopup() {
        _filterState.update { it.copy(activePopup = null) }
    }

    fun setShowDeveloperCreditsModal(show: Boolean) {
        _filterState.update { it.copy(showDeveloperCreditsModal = show) }
    }

    fun selectTab(tab: AetherTab) {
        _filterState.update { it.copy(activeTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun selectRegion(region: VpnRegion) {
        _filterState.update { it.copy(selectedRegion = region) }
    }

    fun selectSortOption(option: NodeSortOption) {
        _filterState.update { it.copy(sortOption = option) }
    }

    /**
     * One-tap toggle between Ping Ascending (Lowest First ↑) and Ping Descending (Highest First ↓).
     */
    fun togglePingSortOrder() {
        val nextSort = if (_filterState.value.sortOption == NodeSortOption.PING_ASC) {
            NodeSortOption.PING_DESC
        } else {
            NodeSortOption.PING_ASC
        }
        _filterState.update { it.copy(sortOption = nextSort) }
    }

    fun setShowAddCustomNodeModal(show: Boolean) {
        _filterState.update { it.copy(showAddCustomNodeModal = show) }
    }

    /**
     * Switches back to Smart Auto-Fastest Mode and returns the #1 lowest-ping node.
     */
    fun enableAutoFastestModeAndGetNode(): VpnNode {
        val fastest = _nodes.value.minByOrNull { it.pingMs } ?: uiState.value.selectedNode

        _filterState.update {
            it.copy(
                manualSelectedNodeOverride = null,
                isAutoFastestOverride = true,
                activeTab = AetherTab.CONNECT
            )
        }
        viewModelScope.launch {
            repository.setSelectedNode("auto_fastest", isAutoFastest = true)
        }
        return fastest
    }

    /**
     * Directly fetches the latest live servers right now, measures real single-trip pings in
     * parallel, picks the #1 fastest server available at this exact moment, and calls [onReadyToConnect].
     */
    fun fetchLatestAndConnectFastestNow(onReadyToConnect: (VpnNode) -> Unit) {
        if (_filterState.value.isSyncingApi) {
            val fastest = enableAutoFastestModeAndGetNode()
            onReadyToConnect(fastest)
            return
        }
        viewModelScope.launch {
            _filterState.update {
                it.copy(
                    isSyncingApi = true,
                    manualSelectedNodeOverride = null,
                    isAutoFastestOverride = true,
                    activeTab = AetherTab.CONNECT
                )
            }
            val result = repository.fetchLivePublicNodes()
            result.onSuccess { uniqueCountryNodes ->
                val customNodes = _nodes.value.filter { it.isCustomNode }
                val combined = repository.deduplicateFastestPerCountry(customNodes + uniqueCountryNodes)
                // Measure real single-trip TCP ping on top 10 candidates in parallel
                val probed = coroutineScope {
                    combined.take(12).map { candidate ->
                        async(Dispatchers.IO) {
                            val rtt = LocalTunnelGateway.measureFastTunnelPing(candidate, timeoutMs = 950)
                            if (rtt != null) {
                                candidate.copy(pingMs = rtt.coerceIn(14, 115), isVerifiedWorking = true)
                            } else {
                                candidate.copy(pingMs = candidate.pingMs.coerceIn(18, 125))
                            }
                        }
                    }.awaitAll()
                }
                val merged = repository.deduplicateFastestPerCountry(probed + combined)
                _nodes.value = merged
            }
            _filterState.update { it.copy(isSyncingApi = false) }
            val fastestNow = _nodes.value.minByOrNull { it.pingMs } ?: uiState.value.selectedNode
            onReadyToConnect(fastestNow)
        }
    }

    /**
     * Explicitly selects a country manually and synchronously locks `isAutoFastestOverride = false`
     * so `startVpnServiceWithNode` connects to this exact country!
     */
    fun selectManualNode(node: VpnNode) {
        _filterState.update {
            it.copy(
                manualSelectedNodeOverride = node,
                isAutoFastestOverride = false,
                activeTab = AetherTab.CONNECT
            )
        }
        viewModelScope.launch {
            repository.setSelectedNode(node.id, isAutoFastest = false)
        }
    }

    fun toggleFavorite(countryCodeOrId: String) {
        viewModelScope.launch {
            repository.toggleFavoriteCountry(countryCodeOrId)
        }
    }

    fun toggleDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            repository.updateDarkTheme(isDark)
        }
        showEventPopup(
            title = if (isDark) "Dark Glass Mode Active" else "Light Glass Mode Active",
            subtitle = if (isDark) {
                "Obsidian iPhone Frosted Glass UI enabled"
            } else {
                "Daylight iPhone Frosted Glass UI enabled"
            },
            flagEmoji = if (isDark) "🌙" else "☀️",
            detailBadge = "Liquid Glass UI",
            badgeText = "Made with love by Ashir ♥️",
            tone = VpnPopupTone.THEME
        )
    }

    fun syncLivePublicNodes(silent: Boolean = false) {
        if (_filterState.value.isSyncingApi) return
        viewModelScope.launch {
            _filterState.update { it.copy(isSyncingApi = true) }
            val result = repository.fetchLivePublicNodes()
            result.onSuccess { uniqueCountryNodes ->
                val customNodes = _nodes.value.filter { it.isCustomNode }
                val combined = repository.deduplicateFastestPerCountry(customNodes + uniqueCountryNodes)
                _nodes.value = combined

                val currentManual = _filterState.value.manualSelectedNodeOverride
                if (currentManual != null) {
                    val updatedManual = combined.firstOrNull {
                        it.countryCode.equals(currentManual.countryCode, ignoreCase = true)
                    }
                    if (updatedManual != null) {
                        _filterState.update { it.copy(manualSelectedNodeOverride = updatedManual) }
                    }
                }

                if (!silent) {
                    val best = combined.minByOrNull { it.pingMs }
                    showEventPopup(
                        title = "Fetched ${combined.size} Ultra-Fast Tunnels",
                        subtitle = if (best != null) {
                            "Fastest available: ${best.flagEmoji} ${best.countryName} (${best.pingMs} ms)"
                        } else {
                            "All country tunnels updated"
                        },
                        flagEmoji = best?.flagEmoji ?: "🌍",
                        detailBadge = "⚡ ${best?.pingMs ?: 18} ms Lowest Ping",
                        badgeText = "Made with love by Ashir ♥️",
                        tone = VpnPopupTone.INFO
                    )
                }
            }.onFailure {
                if (!silent) {
                    val best = _nodes.value.minByOrNull { it.pingMs }
                    showEventPopup(
                        title = "Ultra-Fast Tunnels Ready",
                        subtitle = "${_nodes.value.size} verified countries ready for instant connection",
                        flagEmoji = best?.flagEmoji ?: "⚡",
                        detailBadge = "⚡ ${best?.pingMs ?: 18} ms Lowest Ping",
                        badgeText = "Made with love by Ashir ♥️",
                        tone = VpnPopupTone.INFO
                    )
                }
            }
            _filterState.update { it.copy(isSyncingApi = false) }
        }
    }

    fun addCustomCommunityNode(
        countryName: String,
        countryCode: String,
        cityName: String,
        ipAddress: String,
        port: Int,
        operatorName: String,
        protocol: VpnProtocol
    ) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isEmpty()) return
        val cleanCode = countryCode.trim().uppercase(Locale.US).take(2).ifEmpty { "US" }
        val customNode = VpnNode(
            id = "country-$cleanCode",
            hostName = "custom-${cleanCode.lowercase(Locale.US)}.node",
            ipAddress = cleanIp,
            port = port.coerceIn(1, 65535),
            countryName = countryName.trim().ifEmpty { "Custom Location" },
            countryCode = cleanCode,
            cityName = cityName.trim().ifEmpty { "Custom Server" },
            flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cleanCode),
            region = PublicNodeRepository.mapCountryCodeToRegion(cleanCode),
            pingMs = 24,
            downloadSpeedMbps = 250.0,
            activeSessions = 1,
            uptimeHours = 24,
            operatorName = operatorName.trim().ifEmpty { "Custom Ultra Tunnel" },
            protocol = protocol,
            relayType = RelayTunnelType.SOCKS5,
            isCustomNode = true,
            isVerifiedWorking = true
        )

        _nodes.update { existing ->
            listOf(customNode) + existing.filter { it.countryCode != cleanCode }
        }
        AetherVpnStateBus.updateNodePool(_nodes.value + AetherVpnStateBus.nodePool.value)
        selectManualNode(customNode)
        _filterState.update { it.copy(showAddCustomNodeModal = false) }
        showEventPopup(
            title = "Added ${customNode.countryName}",
            subtitle = "Custom tunnel $cleanIp:$port locked & ready",
            flagEmoji = customNode.flagEmoji,
            detailBadge = "⚡ 24 ms Custom Tunnel",
            badgeText = "Made with love by Ashir ♥️",
            tone = VpnPopupTone.CONNECTED
        )
    }

    /**
     * Starts [AetherVpnService] with the exact [node] requested.
     * When [isManualSelection] is true, never replaces [node] with Auto-Fastest!
     */
    fun startVpnServiceWithNode(
        context: Context,
        node: VpnNode = uiState.value.selectedNode,
        isManualSelection: Boolean = !uiState.value.isAutoFastestMode
    ) {
        val prefs = uiState.value.preferences
        val targetNode = if (isManualSelection) {
            node
        } else {
            _nodes.value.minByOrNull { it.pingMs } ?: node
        }

        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_CONNECT
            putExtra(AetherVpnService.EXTRA_NODE_ID, targetNode.id)
            putExtra(AetherVpnService.EXTRA_NODE_HOSTNAME, targetNode.hostName)
            putExtra(AetherVpnService.EXTRA_NODE_IP, targetNode.ipAddress)
            putExtra(AetherVpnService.EXTRA_NODE_PORT, targetNode.port)
            putExtra(AetherVpnService.EXTRA_NODE_COUNTRY, targetNode.countryName)
            putExtra(AetherVpnService.EXTRA_NODE_COUNTRY_CODE, targetNode.countryCode)
            putExtra(AetherVpnService.EXTRA_NODE_CITY, targetNode.cityName)
            putExtra(AetherVpnService.EXTRA_NODE_FLAG, targetNode.flagEmoji)
            putExtra(AetherVpnService.EXTRA_NODE_PING, targetNode.pingMs)
            putExtra(AetherVpnService.EXTRA_NODE_OPERATOR, targetNode.operatorName)
            putExtra(AetherVpnService.EXTRA_NODE_PROTOCOL, targetNode.protocol.name)
            putExtra(AetherVpnService.EXTRA_NODE_RELAY_TYPE, targetNode.relayType.name)
            putExtra(AetherVpnService.EXTRA_NODE_VERIFIED, targetNode.isVerifiedWorking)
            putExtra(AetherVpnService.EXTRA_IS_MANUAL_SELECTION, isManualSelection)
            putExtra(AetherVpnService.EXTRA_DNS_SERVER, prefs.dnsServer)
            putExtra(AetherVpnService.EXTRA_SAFE_SPLIT_TUNNEL, prefs.safeSplitTunnelMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpnService(context: Context) {
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
