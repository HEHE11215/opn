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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 2-Tab Consumer Navigation for A VPN (Removed the 3rd "Open Audit" tab as requested).
 */
enum class AetherTab(val route: String, val label: String) {
    CONNECT("connect_dashboard", "Connect"),
    NODES("worldwide_nodes", "Servers")
}

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
    val sortOption: NodeSortOption = NodeSortOption.LOWEST_PING,
    val isSyncingApi: Boolean = false,
    val isPingingNodes: Boolean = false,
    val singlePingingNodeId: String? = null,
    val inspectedNode: VpnNode? = null,
    val showAddCustomNodeModal: Boolean = false,
    val bannerFeedback: String? = null
)

private data class FilterBarState(
    val activeTab: AetherTab = AetherTab.CONNECT,
    val searchQuery: String = "",
    val selectedRegion: VpnRegion = VpnRegion.ALL,
    val sortOption: NodeSortOption = NodeSortOption.LOWEST_PING,
    val isSyncingApi: Boolean = false,
    val isPingingNodes: Boolean = false,
    val singlePingingNodeId: String? = null,
    val inspectedNode: VpnNode? = null,
    val showAddCustomNodeModal: Boolean = false,
    val bannerFeedback: String? = null,
    val manualSelectedNodeOverride: VpnNode? = null,
    val isAutoFastestOverride: Boolean? = null
)

/**
 * Clean MVVM ViewModel for A VPN.
 * Fixes the manual server selection race condition by holding an immediate synchronous
 * `manualSelectedNodeOverride` so tapping any country connects directly to that country!
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PublicNodeRepository(application.applicationContext)

    private val _nodes = MutableStateFlow(repository.getVerifiedCommunityNodes())
    private val _filterState = MutableStateFlow(FilterBarState())

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
                NodeSortOption.LOWEST_PING -> matched.sortedWith(
                    compareByDescending<VpnNode> { it.isVerifiedWorking }.thenBy { it.pingMs }
                )
                NodeSortOption.COUNTRY_NAME -> matched.sortedBy { it.countryName.lowercase(Locale.US) }
                NodeSortOption.HIGHEST_SPEED -> matched.sortedWith(
                    compareByDescending<VpnNode> { it.isVerifiedWorking }.thenByDescending { it.downloadSpeedMbps }
                )
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
            isPingingNodes = filter.isPingingNodes,
            singlePingingNodeId = filter.singlePingingNodeId,
            inspectedNode = filter.inspectedNode,
            showAddCustomNodeModal = filter.showAddCustomNodeModal,
            bannerFeedback = filter.bannerFeedback
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

    fun setShowAddCustomNodeModal(show: Boolean) {
        _filterState.update { it.copy(showAddCustomNodeModal = show) }
    }

    fun dismissBannerFeedback() {
        _filterState.update { it.copy(bannerFeedback = null) }
    }

    /**
     * Switches back to Smart Auto-Fastest Mode and returns the fastest node.
     */
    fun enableAutoFastestModeAndGetNode(): VpnNode {
        val fastest = _nodes.value.filter { it.isVerifiedWorking }.minByOrNull { it.pingMs }
            ?: _nodes.value.minByOrNull { it.pingMs }
            ?: uiState.value.selectedNode

        _filterState.update {
            it.copy(
                manualSelectedNodeOverride = null,
                isAutoFastestOverride = true,
                activeTab = AetherTab.CONNECT,
                bannerFeedback = "Auto-Connecting fastest server: ${fastest.flagEmoji} ${fastest.countryName}"
            )
        }
        viewModelScope.launch {
            repository.setSelectedNode("auto_fastest", isAutoFastest = true)
        }
        return fastest
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
                activeTab = AetherTab.CONNECT,
                bannerFeedback = "Selected ${node.flagEmoji} ${node.countryName}"
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

                // If user manually selected a country, refresh its IP/port with the latest fastest server for that country
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
                    val best = combined.firstOrNull()
                    _filterState.update {
                        it.copy(
                            bannerFeedback = if (best != null) {
                                "Updated ${combined.size} countries • Fastest: ${best.flagEmoji} ${best.countryName} (${best.pingMs} ms)"
                            } else {
                                "Updated ${combined.size} countries"
                            }
                        )
                    }
                }
            }.onFailure {
                if (!silent) {
                    _filterState.update {
                        it.copy(
                            bannerFeedback = "Loaded ${uiState.value.allNodes.size} countries"
                        )
                    }
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
            pingMs = 28,
            downloadSpeedMbps = 150.0,
            activeSessions = 1,
            uptimeHours = 24,
            operatorName = operatorName.trim().ifEmpty { "Custom Server" },
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
        _filterState.update {
            it.copy(
                showAddCustomNodeModal = false,
                bannerFeedback = "Added & selected ${customNode.flagEmoji} ${customNode.countryName}"
            )
        }
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
            _nodes.value.filter { it.isVerifiedWorking }.minByOrNull { it.pingMs }
                ?: _nodes.value.minByOrNull { it.pingMs }
                ?: node
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
