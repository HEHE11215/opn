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
import com.example.model.TunnelConnectionStatus
import com.example.model.TunnelTelemetry
import com.example.model.VpnNode
import com.example.model.VpnPreferences
import com.example.model.VpnProtocol
import com.example.model.VpnRegion
import com.example.vpn.AetherVpnService
import com.example.vpn.AetherVpnStateBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Navigation tabs for AetherNode.
 */
enum class AetherTab(val route: String, val label: String) {
    CONNECT("connect_dashboard", "Connect"),
    NODES("worldwide_nodes", "Free Nodes"),
    AUDIT("opensource_audit", "Open Audit")
}

/**
 * Immutable UI state for the entire AetherNode VPN application.
 */
data class VpnUiState(
    val activeTab: AetherTab = AetherTab.CONNECT,
    val allNodes: List<VpnNode> = emptyList(),
    val filteredNodes: List<VpnNode> = emptyList(),
    val selectedNode: VpnNode,
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
    val bannerFeedback: String? = null
)

/**
 * Clean MVVM ViewModel orchestrating public node discovery, live TCP latency testing,
 * DataStore preferences, and Android [AetherVpnService] lifecycle intents.
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
        val selected = nodes.firstOrNull { it.id == prefs.selectedNodeId }
            ?: nodes.first()

        val query = filter.searchQuery.trim().lowercase(Locale.US)
        val filtered = nodes.filter { node ->
            val matchesRegion = when (filter.selectedRegion) {
                VpnRegion.ALL -> true
                VpnRegion.FAVORITES -> prefs.favoriteNodeIds.contains(node.id)
                else -> node.region == filter.selectedRegion
            }
            val matchesQuery = query.isEmpty() ||
                node.countryName.lowercase(Locale.US).contains(query) ||
                node.cityName.lowercase(Locale.US).contains(query) ||
                node.ipAddress.lowercase(Locale.US).contains(query) ||
                node.operatorName.lowercase(Locale.US).contains(query) ||
                node.countryCode.lowercase(Locale.US).contains(query)
            matchesRegion && matchesQuery
        }.let { matched ->
            when (filter.sortOption) {
                NodeSortOption.LOWEST_PING -> matched.sortedBy { it.pingMs }
                NodeSortOption.HIGHEST_SPEED -> matched.sortedByDescending { it.downloadSpeedMbps }
                NodeSortOption.UPTIME -> matched.sortedByDescending { it.uptimeHours }
            }
        }

        VpnUiState(
            activeTab = filter.activeTab,
            allNodes = nodes,
            filteredNodes = filtered,
            selectedNode = selected.copy(protocol = prefs.preferredProtocol),
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
        // Proactively sync live worldwide public VPN Gate nodes in the background on startup
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

    fun inspectNodeConfig(node: VpnNode?) {
        _filterState.update { it.copy(inspectedNode = node) }
    }

    fun setShowAddCustomNodeModal(show: Boolean) {
        _filterState.update { it.copy(showAddCustomNodeModal = show) }
    }

    fun dismissBannerFeedback() {
        _filterState.update { it.copy(bannerFeedback = null) }
    }

    fun selectNode(node: VpnNode) {
        viewModelScope.launch {
            repository.setSelectedNode(node.id)
            AetherVpnStateBus.appendLog(
                tag = "NODE",
                message = "Selected relay node: ${node.flagEmoji} ${node.cityName}, ${node.countryName} (${node.ipAddress}:${node.port})"
            )
            // If tunnel is currently active, seamlessly switch to the newly selected node
            val currentStatus = uiState.value.telemetry.status
            if (currentStatus == TunnelConnectionStatus.CONNECTED ||
                currentStatus == TunnelConnectionStatus.CONNECTING
            ) {
                startVpnServiceWithNode(getApplication(), node.copy(protocol = uiState.value.preferences.preferredProtocol))
                _filterState.update {
                    it.copy(
                        activeTab = AetherTab.CONNECT,
                        bannerFeedback = "Switched encrypted tunnel to ${node.flagEmoji} ${node.cityName}"
                    )
                }
            }
        }
    }

    fun toggleFavorite(nodeId: String) {
        viewModelScope.launch {
            repository.toggleFavoriteNode(nodeId)
        }
    }

    fun updatePreferredProtocol(protocol: VpnProtocol) {
        viewModelScope.launch {
            repository.updateProtocol(protocol)
            AetherVpnStateBus.appendLog(
                tag = "CRYPTO",
                message = "Updated preferred tunnel protocol to ${protocol.fullName} (${protocol.cipherDescription})"
            )
        }
    }

    fun updateDnsServer(dnsServer: String) {
        viewModelScope.launch {
            repository.updateDnsServer(dnsServer)
            AetherVpnStateBus.appendLog("DNS", "Configured tunnel DNS resolver to $dnsServer")
        }
    }

    fun updateSafeSplitTunnel(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSafeSplitTunnel(enabled)
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoReconnect(enabled)
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
            result.onSuccess { mergedNodes ->
                // Preserve any custom user-added nodes
                val customNodes = _nodes.value.filter { it.isCustomNode }
                val combined = (customNodes + mergedNodes).distinctBy { it.ipAddress }
                _nodes.value = combined
                val liveCount = combined.count { it.isLiveFromPublicApi }
                if (!silent) {
                    _filterState.update {
                        it.copy(
                            bannerFeedback = if (liveCount > 0) {
                                "Synced $liveCount live VPN Gate nodes + ${combined.size - liveCount} verified global nodes"
                            } else {
                                "Verified ${combined.size} worldwide open-source community relay nodes"
                            }
                        )
                    }
                }
            }.onFailure {
                if (!silent) {
                    _filterState.update {
                        it.copy(
                            bannerFeedback = "Using ${uiState.value.allNodes.size} verified built-in worldwide relay nodes"
                        )
                    }
                }
            }
            _filterState.update { it.copy(isSyncingApi = false) }
        }
    }

    fun pingAllNodes() {
        if (_filterState.value.isPingingNodes) return
        viewModelScope.launch {
            _filterState.update { it.copy(isPingingNodes = true) }
            val updated = repository.pingAllNodesConcurrently(_nodes.value)
            _nodes.value = updated
            val fastest = updated.minByOrNull { it.pingMs }
            _filterState.update {
                it.copy(
                    isPingingNodes = false,
                    bannerFeedback = fastest?.let { f ->
                        "Latency sweep complete • Best: ${f.flagEmoji} ${f.cityName} (${f.pingMs} ms)"
                    }
                )
            }
        }
    }

    fun pingSingleNode(node: VpnNode) {
        viewModelScope.launch {
            _filterState.update { it.copy(singlePingingNodeId = node.id) }
            val newPing = repository.measureNodeLatencyMs(node)
            _nodes.update { list ->
                list.map { existing ->
                    if (existing.id == node.id) existing.copy(pingMs = newPing) else existing
                }
            }
            AetherVpnStateBus.appendLog(
                tag = "PING",
                message = "Measured RTT to ${node.flagEmoji} ${node.cityName} (${node.ipAddress}:${node.port}) -> $newPing ms"
            )
            _filterState.update {
                it.copy(
                    singlePingingNodeId = null,
                    bannerFeedback = "${node.flagEmoji} ${node.cityName} latency: $newPing ms"
                )
            }
        }
    }

    fun selectFastestNodeAndGet(): VpnNode {
        val fastest = _nodes.value.minByOrNull { it.pingMs } ?: uiState.value.selectedNode
        viewModelScope.launch {
            repository.setSelectedNode(fastest.id)
        }
        return fastest.copy(protocol = uiState.value.preferences.preferredProtocol)
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
        val cleanCode = countryCode.trim().uppercase(Locale.US).take(2).ifEmpty { "UN" }
        val customNode = VpnNode(
            id = "custom-${System.currentTimeMillis()}",
            hostName = "custom-${cleanCode.lowercase(Locale.US)}.community.node",
            ipAddress = cleanIp,
            port = port.coerceIn(1, 65535),
            countryName = countryName.trim().ifEmpty { "Custom Relay" },
            countryCode = cleanCode,
            cityName = cityName.trim().ifEmpty { "Community Endpoint" },
            flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cleanCode),
            region = PublicNodeRepository.mapCountryCodeToRegion(cleanCode),
            pingMs = 28,
            downloadSpeedMbps = 150.0,
            activeSessions = 1,
            uptimeHours = 24,
            operatorName = operatorName.trim().ifEmpty { "User Contributed Node" },
            protocol = protocol,
            openVpnConfigPreview = PublicNodeRepository.defaultOpenVpnPreview(cleanIp, port, protocol),
            isCustomNode = true
        )

        _nodes.update { listOf(customNode) + it }
        viewModelScope.launch {
            repository.setSelectedNode(customNode.id)
            val measured = repository.measureNodeLatencyMs(customNode)
            _nodes.update { list ->
                list.map { if (it.id == customNode.id) it.copy(pingMs = measured) else it }
            }
        }
        AetherVpnStateBus.appendLog(
            tag = "CUSTOM",
            message = "Added custom community node ${customNode.flagEmoji} ${customNode.cityName} ($cleanIp:$port)",
            isHighlight = true
        )
        _filterState.update {
            it.copy(
                showAddCustomNodeModal = false,
                bannerFeedback = "Added & selected ${customNode.flagEmoji} ${customNode.cityName} ($cleanIp)"
            )
        }
    }

    fun clearAuditLogs() {
        AetherVpnStateBus.clearLogs()
    }

    /**
     * Dispatches an intent to [AetherVpnService] to start the encrypted TUN session.
     * Uses `startForegroundService` on Android 8.0+ (API 26+) and `startService` on API 24–25.
     */
    fun startVpnServiceWithNode(context: Context, node: VpnNode = uiState.value.selectedNode) {
        val prefs = uiState.value.preferences
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_CONNECT
            putExtra(AetherVpnService.EXTRA_NODE_ID, node.id)
            putExtra(AetherVpnService.EXTRA_NODE_HOSTNAME, node.hostName)
            putExtra(AetherVpnService.EXTRA_NODE_IP, node.ipAddress)
            putExtra(AetherVpnService.EXTRA_NODE_PORT, node.port)
            putExtra(AetherVpnService.EXTRA_NODE_COUNTRY, node.countryName)
            putExtra(AetherVpnService.EXTRA_NODE_COUNTRY_CODE, node.countryCode)
            putExtra(AetherVpnService.EXTRA_NODE_CITY, node.cityName)
            putExtra(AetherVpnService.EXTRA_NODE_FLAG, node.flagEmoji)
            putExtra(AetherVpnService.EXTRA_NODE_PING, node.pingMs)
            putExtra(AetherVpnService.EXTRA_NODE_OPERATOR, node.operatorName)
            putExtra(AetherVpnService.EXTRA_NODE_PROTOCOL, node.protocol.name)
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
