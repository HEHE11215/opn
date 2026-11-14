package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PublicNodeRepository
import com.example.model.NodeSortOption
import com.example.model.RelayTunnelType
import com.example.model.TunnelConnectionStatus
import com.example.model.TunnelTelemetry
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import com.example.model.VpnPreferences
import com.example.model.VpnProtocol
import com.example.model.VpnRegion
import com.example.vpn.AVpnQuickTileService
import com.example.vpn.AetherVpnService
import com.example.vpn.AetherVpnStateBus
import com.example.vpn.BackgroundLatencyWorkScheduler
import com.example.vpn.LocalTunnelGateway
import com.example.vpn.ServerLatencyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 3-Tab Ultra-Light Navigation for A VPN.
 */
enum class AetherTab(val route: String, val label: String) {
    CONNECT("connect_dashboard", "Connect"),
    NODES("worldwide_nodes", "Servers"),
    LOGS("connection_log", "Logs")
}

enum class VpnPopupTone {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    THEME,
    INFO
}

/**
 * Full-Screen Optimized Glass Popup Notification shown whenever an event occurs.
 */
data class VpnEventPopup(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val subtitle: String,
    val flagEmoji: String = "🔥",
    val detailBadge: String = "Verified Exit Tunnel",
    val badgeText: String = "Developed by Ashir ♥️",
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
    val preferences: VpnPreferences = VpnPreferences(),
    val searchQuery: String = "",
    val selectedRegion: VpnRegion = VpnRegion.ALL,
    val sortOption: NodeSortOption = NodeSortOption.PING_ASC,
    val isSyncingApi: Boolean = false,
    val showAddCustomNodeModal: Boolean = false,
    val showDeveloperCreditsModal: Boolean = false,
    val showFreeSshFetcherModal: Boolean = false,
    val isFetchingSshAccounts: Boolean = false,
    val sshCaptchaGatedProviders: List<String> = emptyList(),
    val isProbingLatency: Boolean = false,
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
    val showFreeSshFetcherModal: Boolean = false,
    val isFetchingSshAccounts: Boolean = false,
    val sshCaptchaGatedProviders: List<String> = emptyList(),
    val isProbingLatency: Boolean = false,
    val activePopup: VpnEventPopup? = null,
    val manualSelectedNodeOverride: VpnNode? = null,
    val isAutoFastestOverride: Boolean? = null
)

/**
 * MVVM ViewModel for **A VPN** (Developed by Ashir ♥️) backed by strict TLS-verified remote relays
 * and continuous real-time TCP round-trip ping monitoring.
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PublicNodeRepository(application.applicationContext)
    private val latencyWorker = ServerLatencyWorker(viewModelScope)

    private val _nodes = MutableStateFlow<List<VpnNode>>(emptyList())
    private val _customNodes = MutableStateFlow<List<VpnNode>>(emptyList())
    private val _filterState = MutableStateFlow(FilterBarState())
    private val _pendingStartupConnect = MutableStateFlow<Pair<VpnNode, Boolean>?>(null)
    val pendingStartupConnect: StateFlow<Pair<VpnNode, Boolean>?> = _pendingStartupConnect.asStateFlow()

    private var lastObservedStatus: TunnelConnectionStatus = TunnelConnectionStatus.DISCONNECTED
    private var lastConnectedNodeKey: String? = null
    @Volatile
    private var wifiOnlyLatencyEnabled: Boolean = false
    @Volatile
    private var startupAutoConnectConsumed: Boolean = false

    val uiState: StateFlow<VpnUiState> = combine(
        _nodes,
        _filterState,
        repository.preferencesFlow,
        AetherVpnStateBus.telemetry
    ) { nodes, filter, prefs, telemetry ->
        val fastestVerified = nodes.filter { it.isVerifiedWorking && it.pingMs > 0 }
            .minByOrNull { it.pingMs }
            ?: nodes.filter { it.pingMs > 0 }.minByOrNull { it.pingMs }
            ?: nodes.firstOrNull()
            ?: repository.getInitialBootstrapNode()

        val isAutoMode = filter.isAutoFastestOverride
            ?: (prefs.autoSelectFastest && filter.manualSelectedNodeOverride == null)

        // While CONNECTED or CONNECTING, strictly lock onto telemetry.activeNode so background ping scans never jump servers!
        val activeConnectedNode = telemetry.activeNode?.takeIf {
            (telemetry.status == TunnelConnectionStatus.CONNECTED ||
                telemetry.status == TunnelConnectionStatus.CONNECTING) &&
                it.ipAddress.isNotBlank()
        }

        val selected = if (activeConnectedNode != null) {
            val latestPingMatch = nodes.firstOrNull {
                it.ipAddress == activeConnectedNode.ipAddress && it.port == activeConnectedNode.port
            }
            if (latestPingMatch != null && latestPingMatch.pingMs > 0) {
                activeConnectedNode.copy(pingMs = latestPingMatch.pingMs)
            } else {
                activeConnectedNode
            }
        } else if (!isAutoMode) {
            val manualOverride = filter.manualSelectedNodeOverride
            if (manualOverride != null) {
                val exactMatch = nodes.firstOrNull {
                    it.ipAddress == manualOverride.ipAddress && it.port == manualOverride.port
                }
                if (exactMatch != null) {
                    manualOverride.copy(pingMs = exactMatch.pingMs)
                } else {
                    nodes.firstOrNull {
                        it.id == manualOverride.id ||
                            it.countryCode.equals(manualOverride.countryCode, ignoreCase = true)
                    } ?: manualOverride
                }
            } else {
                nodes.firstOrNull {
                    it.id == prefs.selectedNodeId ||
                        it.countryCode.equals(prefs.selectedNodeId.removePrefix("country-"), ignoreCase = true)
                } ?: prefs.lastSelectedNode ?: fastestVerified
            }
        } else {
            if (nodes.isNotEmpty()) fastestVerified else (prefs.lastSelectedNode ?: fastestVerified)
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
                node.countryCode.lowercase(Locale.US).contains(query) ||
                node.ipAddress.contains(query)
            matchesRegion && matchesQuery
        }.let { matched ->
            when (filter.sortOption) {
                NodeSortOption.PING_ASC -> matched.sortedBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                NodeSortOption.PING_DESC -> matched.sortedByDescending { if (it.pingMs > 0) it.pingMs else -1 }
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
            preferences = prefs,
            searchQuery = filter.searchQuery,
            selectedRegion = filter.selectedRegion,
            sortOption = filter.sortOption,
            isSyncingApi = filter.isSyncingApi,
            showAddCustomNodeModal = filter.showAddCustomNodeModal,
            showDeveloperCreditsModal = filter.showDeveloperCreditsModal,
            showFreeSshFetcherModal = filter.showFreeSshFetcherModal,
            isFetchingSshAccounts = filter.isFetchingSshAccounts,
            sshCaptchaGatedProviders = filter.sshCaptchaGatedProviders,
            isProbingLatency = filter.isProbingLatency,
            activePopup = filter.activePopup
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = VpnUiState(
            allNodes = emptyList(),
            filteredNodes = emptyList(),
            selectedNode = AetherVpnStateBus.telemetry.value.activeNode ?: repository.getInitialBootstrapNode(),
            telemetry = AetherVpnStateBus.telemetry.value,
            isSyncingApi = true
        )
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val directIp = LocalTunnelGateway.fetchDeviceDirectIp()
            if (directIp != null) {
                AetherVpnStateBus.updateTelemetry { it.copy(deviceDirectIp = directIp) }
            }
        }
        syncLivePublicNodes(silent = true)
        startRealtimePingMonitor()

        viewModelScope.launch {
            val initialPrefs = runCatching { repository.preferencesFlow.first() }.getOrNull()
            if (initialPrefs != null && !startupAutoConnectConsumed) {
                startupAutoConnectConsumed = true
                if (initialPrefs.autoConnectOnStartup) {
                    val statusNow = AetherVpnStateBus.telemetry.value.status
                    if (statusNow != TunnelConnectionStatus.CONNECTED &&
                        statusNow != TunnelConnectionStatus.CONNECTING
                    ) {
                        val isManual = !initialPrefs.autoSelectFastest
                        val savedTarget = initialPrefs.lastSelectedNode?.takeIf { it.ipAddress.isNotBlank() }
                        val resolvedTarget = savedTarget ?: run {
                            val liveList = _nodes.first { list -> list.any { it.ipAddress.isNotBlank() } }
                            if (isManual) {
                                liveList.firstOrNull {
                                    it.id == initialPrefs.selectedNodeId ||
                                        it.countryCode.equals(
                                            initialPrefs.selectedNodeId.removePrefix("country-"),
                                            ignoreCase = true
                                        )
                                } ?: liveList.firstOrNull { it.ipAddress.isNotBlank() }
                            } else {
                                liveList.filter { it.isVerifiedWorking && it.pingMs > 0 }
                                    .minByOrNull { it.pingMs }
                                    ?: liveList.firstOrNull { it.ipAddress.isNotBlank() }
                            }
                        }
                        if (resolvedTarget != null) {
                            AetherVpnStateBus.recordDiagnostic(
                                phase = "Auto-Connect Startup",
                                serverLabel = "${resolvedTarget.flagEmoji} ${resolvedTarget.countryName}",
                                summary = "Auto-connecting on app launch",
                                detail = "Connecting last selected server (${resolvedTarget.ipAddress}:${resolvedTarget.port}) per startup preference.",
                                severity = VpnDiagnosticSeverity.INFO
                            )
                            _pendingStartupConnect.value = resolvedTarget to isManual
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.preferencesFlow
                .map { it.wifiOnlyLatencyCheck }
                .distinctUntilChanged()
                .collect { wifiOnly ->
                    wifiOnlyLatencyEnabled = wifiOnly
                    BackgroundLatencyWorkScheduler.scheduleOrUpdate(getApplication(), wifiOnly)
                }
        }

        viewModelScope.launch {
            AetherVpnStateBus.telemetry.collect { tel ->
                val newStatus = tel.status
                val node = tel.activeNode
                val nodeKey = node?.let { "${it.ipAddress}:${it.port}" }
                if (newStatus != lastObservedStatus ||
                    (newStatus == TunnelConnectionStatus.CONNECTED && nodeKey != lastConnectedNodeKey)
                ) {
                    when (newStatus) {
                        TunnelConnectionStatus.CONNECTED -> {
                            if (node != null) {
                                val wasSwitch = lastObservedStatus == TunnelConnectionStatus.CONNECTED
                                lastConnectedNodeKey = nodeKey
                                val exitIp = tel.verifiedExitIp ?: node.verifiedExitIp.ifEmpty { node.ipAddress }
                                val pingLabel = if (node.pingMs > 0) "${node.pingMs} ms" else "Live"
                                showEventPopup(
                                    title = if (wasSwitch) {
                                        "Switched to ${node.countryName}!"
                                    } else {
                                        "Connected to ${node.countryName}!"
                                    },
                                    subtitle = "Verified Exit IP: $exitIp • ${node.cityName}",
                                    flagEmoji = node.flagEmoji,
                                    detailBadge = "⚡ $pingLabel • ${node.relayType.badgeLabel}",
                                    badgeText = "Developed by Ashir ♥️",
                                    tone = VpnPopupTone.CONNECTED
                                )
                            }
                        }

                        TunnelConnectionStatus.DISCONNECTED -> {
                            if (lastObservedStatus == TunnelConnectionStatus.CONNECTED ||
                                lastObservedStatus == TunnelConnectionStatus.CONNECTING
                            ) {
                                lastConnectedNodeKey = null
                                showEventPopup(
                                    title = "VPN Disconnected",
                                    subtitle = "Tunnel closed safely • Tap button anytime to reconnect",
                                    flagEmoji = "🛡️",
                                    detailBadge = "Standby Mode",
                                    badgeText = "Developed by Ashir ♥️",
                                    tone = VpnPopupTone.DISCONNECTED
                                )
                            }
                        }

                        TunnelConnectionStatus.ERROR -> {
                            lastConnectedNodeKey = null
                            showEventPopup(
                                title = "Relay Verification Failed",
                                subtitle = tel.errorMessage ?: "Selected relay unreachable. Scan servers or add a custom proxy.",
                                flagEmoji = "⚠️",
                                detailBadge = "Zero-Leak Protection Active",
                                badgeText = "Developed by Ashir ♥️",
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

    /**
     * Starts the Coroutine-based [ServerLatencyWorker] to periodically measure genuine TCP handshake
     * latency (`System.nanoTime()`) to every server in the list and update the UI in real time.
     */
    private fun startRealtimePingMonitor() {
        latencyWorker.start(
            getNodes = { _nodes.value },
            shouldSkipCycle = {
                _filterState.value.isSyncingApi ||
                    BackgroundLatencyWorkScheduler.isRestrictedByWifiOnly(
                        getApplication(),
                        wifiOnlyLatencyEnabled
                    )
            },
            onMeasured = { updatedNodes ->
                val merged = repository.deduplicateFastestPerCountry(_customNodes.value + updatedNodes)
                _nodes.value = merged
                AetherVpnStateBus.updateNodePool(merged)

                val currentManual = _filterState.value.manualSelectedNodeOverride
                if (currentManual != null) {
                    // Only update pingMs if the exact same IP:port was measured; never replace with a different relay!
                    val exactManualMatch = updatedNodes.firstOrNull {
                        it.ipAddress == currentManual.ipAddress && it.port == currentManual.port
                    }
                    if (exactManualMatch != null && exactManualMatch.pingMs > 0) {
                        _filterState.update {
                            it.copy(manualSelectedNodeOverride = currentManual.copy(pingMs = exactManualMatch.pingMs))
                        }
                    }
                }
            }
        )
    }

    fun showEventPopup(
        title: String,
        subtitle: String,
        flagEmoji: String = "🛡️",
        detailBadge: String = "Verified Exit Tunnel",
        badgeText: String = "Developed by Ashir ♥️",
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

    fun clearDiagnostics() {
        AetherVpnStateBus.clearDiagnostics()
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

    fun setShowFreeSshFetcherModal(show: Boolean) {
        _filterState.update { it.copy(showFreeSshFetcherModal = show) }
    }

    fun enableAutoFastestModeAndGetNode(): VpnNode {
        val fastest = _nodes.value.filter { it.isVerifiedWorking && it.pingMs > 0 }
            .minByOrNull { it.pingMs }
            ?: _nodes.value.firstOrNull()
            ?: uiState.value.selectedNode

        _filterState.update {
            it.copy(
                manualSelectedNodeOverride = null,
                isAutoFastestOverride = true,
                activeTab = AetherTab.CONNECT
            )
        }
        viewModelScope.launch {
            repository.setSelectedNode(
                nodeId = "auto_fastest",
                isAutoFastest = true,
                node = fastest.takeIf { it.ipAddress.isNotBlank() }
            )
        }
        return fastest
    }

    fun fetchLatestAndConnectFastestNow(onReadyToConnect: (VpnNode) -> Unit) {
        val existingFastest = _nodes.value.filter { it.isVerifiedWorking && it.ipAddress.isNotBlank() }
            .minByOrNull { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
        if (existingFastest != null && _filterState.value.isSyncingApi) {
            onReadyToConnect(enableAutoFastestModeAndGetNode())
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

            val directIp = AetherVpnStateBus.telemetry.value.deviceDirectIp
            repository.fetchLivePublicNodes(
                deviceDirectIp = directIp,
                onWaveVerified = { waveNodes ->
                    val merged = repository.deduplicateFastestPerCountry(_customNodes.value + waveNodes)
                    _nodes.value = merged
                    AetherVpnStateBus.updateNodePool(merged)
                }
            )
            _filterState.update { it.copy(isSyncingApi = false) }

            val bestNow = _nodes.value.filter { it.isVerifiedWorking && it.ipAddress.isNotBlank() }
                .minByOrNull { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                ?: _nodes.value.firstOrNull { it.ipAddress.isNotBlank() }

            if (bestNow != null) {
                onReadyToConnect(bestNow)
            } else {
                showEventPopup(
                    title = "No Live Public Relays Found",
                    subtitle = "Tap Servers → Refresh or tap + to add a working SOCKS5/HTTPS proxy.",
                    flagEmoji = "🌐",
                    detailBadge = "Scan Complete",
                    badgeText = "Made with love by Ashir ♥️",
                    tone = VpnPopupTone.INFO
                )
            }
        }
    }

    fun selectManualNode(node: VpnNode) {
        _filterState.update {
            it.copy(
                manualSelectedNodeOverride = node,
                isAutoFastestOverride = false,
                activeTab = AetherTab.CONNECT
            )
        }
        viewModelScope.launch {
            repository.setSelectedNode(node.id, isAutoFastest = false, node = node)
        }
        probeSelectedNodeLatencyNow(node)
    }

    /**
     * Measures a live protected TCP 3-way handshake ping in milliseconds for the currently selected
     * (or active) relay node so the Real-Time Latency Monitor gauge updates immediately on demand.
     */
    fun probeSelectedNodeLatencyNow(targetOverride: VpnNode? = null) {
        val target = targetOverride ?: uiState.value.selectedNode
        if (target.ipAddress.isBlank() || target.port !in 1..65535) return
        if (_filterState.value.isProbingLatency) return

        viewModelScope.launch(Dispatchers.IO) {
            _filterState.update { it.copy(isProbingLatency = true) }
            val measuredMs = LocalTunnelGateway.measureTcpHandshakeDurationMs(
                ipOrHost = target.ipAddress,
                port = target.port,
                timeoutMs = 2000,
                protectSocket = AetherVpnStateBus.protectSocketCallback
            )
            if (measuredMs != null) {
                val updatedPool = _nodes.value.map { n ->
                    if (n.ipAddress == target.ipAddress && n.port == target.port) {
                        n.copy(pingMs = measuredMs)
                    } else {
                        n
                    }
                }
                _nodes.value = updatedPool
                AetherVpnStateBus.updateNodePool(updatedPool)
                _filterState.update { state ->
                    val currentManual = state.manualSelectedNodeOverride
                    if (currentManual != null &&
                        currentManual.ipAddress == target.ipAddress &&
                        currentManual.port == target.port
                    ) {
                        state.copy(manualSelectedNodeOverride = currentManual.copy(pingMs = measuredMs))
                    } else {
                        state
                    }
                }
                AetherVpnStateBus.updateTelemetry { tel ->
                    val act = tel.activeNode
                    if (act != null && act.ipAddress == target.ipAddress && act.port == target.port) {
                        tel.copy(activeNode = act.copy(pingMs = measuredMs))
                    } else {
                        tel
                    }
                }
            }
            _filterState.update { it.copy(isProbingLatency = false) }
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

    fun setWifiOnlyLatencyCheck(wifiOnly: Boolean) {
        wifiOnlyLatencyEnabled = wifiOnly
        BackgroundLatencyWorkScheduler.scheduleOrUpdate(getApplication(), wifiOnly)
        AetherVpnStateBus.recordDiagnostic(
            phase = "Battery Optimization",
            serverLabel = "WorkManager Policy",
            summary = if (wifiOnly) "Wi-Fi Only Latency Checks Enabled" else "All-Network Latency Checks Enabled",
            detail = if (wifiOnly) {
                "Background latency checks constrained to NetworkType.UNMETERED + battery-not-low."
            } else {
                "Background latency checks allowed on any connected network (NetworkType.CONNECTED)."
            },
            severity = VpnDiagnosticSeverity.INFO
        )
        viewModelScope.launch {
            repository.updateWifiOnlyLatencyCheck(wifiOnly)
        }
    }

    fun setAutoConnectOnStartup(enabled: Boolean) {
        val lastServer = uiState.value.preferences.lastSelectedNode
            ?: uiState.value.selectedNode.takeIf { it.ipAddress.isNotBlank() }
        val serverSummary = lastServer?.let { "${it.flagEmoji} ${it.countryName}" } ?: "last selected server"
        AetherVpnStateBus.recordDiagnostic(
            phase = "Startup Policy",
            serverLabel = "Auto-Connect",
            summary = if (enabled) "Auto-Connect on Startup Enabled" else "Auto-Connect on Startup Disabled",
            detail = if (enabled) {
                "App will automatically connect using $serverSummary upon launch."
            } else {
                "Manual orb tap required when app opens."
            },
            severity = VpnDiagnosticSeverity.INFO
        )
        viewModelScope.launch {
            if (lastServer != null && lastServer.ipAddress.isNotBlank()) {
                repository.saveLastSelectedNode(lastServer)
            }
            repository.updateAutoConnectOnStartup(enabled)
        }
    }

    fun consumePendingStartupConnect() {
        _pendingStartupConnect.value = null
    }

    fun syncLivePublicNodes(silent: Boolean = false) {
        if (_filterState.value.isSyncingApi) return
        viewModelScope.launch {
            _filterState.update { it.copy(isSyncingApi = true) }
            val directIp = AetherVpnStateBus.telemetry.value.deviceDirectIp
            val feedUrl = uiState.value.preferences.sshProviderFeedUrl

            val result = repository.fetchLivePublicNodes(
                deviceDirectIp = directIp,
                sshFeedUrl = feedUrl,
                onWaveVerified = { waveNodes ->
                    val combined = repository.deduplicateFastestPerCountry(_customNodes.value + waveNodes)
                    _nodes.value = combined
                    AetherVpnStateBus.updateNodePool(combined)
                }
            )

            result.onSuccess { verifiedCountries ->
                val combined = repository.deduplicateFastestPerCountry(_customNodes.value + verifiedCountries)
                _nodes.value = combined
                AetherVpnStateBus.updateNodePool(combined)

                val currentManual = _filterState.value.manualSelectedNodeOverride
                if (currentManual != null && !currentManual.isCustomNode) {
                    val updatedManual = combined.firstOrNull {
                        it.countryCode.equals(currentManual.countryCode, ignoreCase = true)
                    }
                    if (updatedManual != null) {
                        _filterState.update { it.copy(manualSelectedNodeOverride = updatedManual) }
                    }
                }

                if (!silent) {
                    val best = combined.filter { it.pingMs > 0 }.minByOrNull { it.pingMs }
                    showEventPopup(
                        title = if (combined.isNotEmpty()) "${combined.size} Verified Servers Ready" else "No Relays Online",
                        subtitle = if (best != null) {
                            "Fastest: ${best.flagEmoji} ${best.countryName} (${best.pingMs} ms • Exit ${best.verifiedExitIp.ifEmpty { best.ipAddress }})"
                        } else {
                            "Public feeds timed out. Try Refresh again or add a Custom SOCKS5/HTTPS server."
                        },
                        flagEmoji = best?.flagEmoji ?: "🌐",
                        detailBadge = if (best != null) "⚡ ${best.pingMs} ms Real-Time" else "Scan Finished",
                        badgeText = "Made with love by Ashir ♥️",
                        tone = VpnPopupTone.INFO
                    )
                }
            }
            result.onFailure { err ->
                if (!silent) {
                    showEventPopup(
                        title = "Server Sync Error",
                        subtitle = err.localizedMessage ?: "Check internet connection and retry",
                        flagEmoji = "⚠️",
                        detailBadge = "Network Notice",
                        badgeText = "Made with love by Ashir ♥️",
                        tone = VpnPopupTone.DISCONNECTED
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
        protocol: VpnProtocol,
        relayType: RelayTunnelType = RelayTunnelType.SSH_TLS,
        username: String = "",
        password: String = "",
        sshPrivateKey: String = "",
        sniHost: String = "",
        udpPort: Int = 7300
    ) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isEmpty()) return
        val cleanCode = countryCode.trim().uppercase(Locale.US).take(2).ifEmpty { "XX" }
        val safePort = port.coerceIn(1, 65535)

        viewModelScope.launch(Dispatchers.IO) {
            val baseNode = VpnNode(
                id = "custom-$cleanIp-$safePort",
                hostName = cleanIp,
                ipAddress = cleanIp,
                port = safePort,
                countryName = countryName.trim().ifEmpty { PublicNodeRepository.countryNameFromCode(cleanCode) },
                countryCode = cleanCode,
                cityName = cityName.trim().ifEmpty { "Custom SSH Server" },
                flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cleanCode),
                region = PublicNodeRepository.mapCountryCodeToRegion(cleanCode),
                pingMs = -1,
                downloadSpeedMbps = 500.0,
                operatorName = operatorName.trim().ifEmpty { "Custom SSH (${relayType.badgeLabel})" },
                protocol = protocol,
                relayType = relayType,
                username = username.trim(),
                password = password,
                sshPrivateKey = sshPrivateKey.trim(),
                sniHost = sniHost.trim().ifEmpty { cleanIp },
                tlsPort = if (relayType == RelayTunnelType.SSH_TLS) safePort else 443,
                udpPort = udpPort.coerceIn(1, 65535),
                providerName = operatorName.trim().ifEmpty { "Custom Server" },
                isCustomNode = true,
                isVerifiedWorking = false
            )

            val directIp = AetherVpnStateBus.telemetry.value.deviceDirectIp
            val e2eResult = LocalTunnelGateway.verifyNodeEndToEndTls(
                node = baseNode,
                deviceDirectIp = directIp,
                timeoutMs = 5500,
                protectSocket = AetherVpnStateBus.protectSocketCallback,
                protectDatagram = AetherVpnStateBus.protectDatagramCallback
            )
            val fallbackTcpPing = if (e2eResult == null) {
                LocalTunnelGateway.measureTcpHandshakeDurationMs(
                    ipOrHost = cleanIp,
                    port = safePort,
                    timeoutMs = 2000,
                    protectSocket = AetherVpnStateBus.protectSocketCallback
                )
            } else {
                null
            }

            val customNode = baseNode.copy(
                relayType = e2eResult?.relayType ?: relayType,
                pingMs = e2eResult?.realTcpPingMs ?: fallbackTcpPing ?: -1,
                verifiedExitIp = e2eResult?.verifiedExitIp.orEmpty(),
                isVerifiedWorking = e2eResult != null
            )

            if (customNode.isSshProtocol && customNode.hasValidEphemeralAccount()) {
                com.example.vpn.EphemeralAccountProvisioner.saveAccountToVault(getApplication(), customNode)
            }

            _customNodes.update { existing ->
                listOf(customNode) + existing.filterNot { it.ipAddress == cleanIp && it.port == safePort }
            }
            val merged = repository.deduplicateFastestPerCountry(_customNodes.value + _nodes.value)
            _nodes.value = merged
            AetherVpnStateBus.updateNodePool(merged)

            selectManualNode(customNode)
            _filterState.update { it.copy(showAddCustomNodeModal = false) }
            showEventPopup(
                title = if (customNode.isVerifiedWorking) {
                    "Verified ${customNode.countryName}!"
                } else {
                    "Added ${customNode.countryName}"
                },
                subtitle = if (customNode.isVerifiedWorking) {
                    "Exit IP ${customNode.verifiedExitIp} • ${customNode.relayType.badgeLabel}"
                } else if (customNode.pingMs > 0) {
                    "$cleanIp:$safePort reachable (${customNode.pingMs} ms), TLS/SSH check pending"
                } else {
                    "$cleanIp:$safePort unreachable — check host, port, or credentials"
                },
                flagEmoji = customNode.flagEmoji,
                detailBadge = if (customNode.pingMs > 0) "⚡ ${customNode.pingMs} ms Live" else "Custom Server",
                badgeText = "Developed by Ashir ♥️",
                tone = if (customNode.isVerifiedWorking) VpnPopupTone.CONNECTED else VpnPopupTone.INFO
            )
        }
    }

    /**
     * Fetches live free 1-day/3-day/7-day SSH/TLS/UDP accounts over the internet (from configured
     * SSH API/feed URLs and non-CAPTCHA free SSH provider forms). If web providers are CAPTCHA-gated,
     * opens the 1-Tap Free SSH Provider Auto-Grabber UI so the user can auto-fill & grab cleanly.
     */
    fun fetchFreeSshFromInternetNow(customFeedUrl: String? = null) {
        viewModelScope.launch {
            val effectiveFeed = (customFeedUrl ?: uiState.value.preferences.sshProviderFeedUrl).trim()
            if (customFeedUrl != null) {
                repository.updateSshProviderFeedUrl(effectiveFeed)
            }
            _filterState.update { it.copy(isFetchingSshAccounts = true) }

            val harvest = com.example.vpn.EphemeralAccountProvisioner.fetchLiveSshAccountsFromInternet(
                context = getApplication(),
                customFeedUrl = effectiveFeed
            )

            val readySsh = harvest.readyNodes
            if (readySsh.isNotEmpty()) {
                _customNodes.update { existing ->
                    (readySsh + existing).distinctBy { "${it.ipAddress}:${it.port}:${it.username}" }
                }
                val merged = repository.deduplicateFastestPerCountry(_customNodes.value + _nodes.value)
                _nodes.value = merged
                AetherVpnStateBus.updateNodePool(merged)

                val primarySsh = readySsh.first()
                selectManualNode(primarySsh)
                _filterState.update {
                    it.copy(
                        isFetchingSshAccounts = false,
                        sshCaptchaGatedProviders = harvest.captchaGatedProviders,
                        showFreeSshFetcherModal = false
                    )
                }
                showEventPopup(
                    title = "Fetched ${readySsh.size} Live SSH Account${if (readySsh.size > 1) "s" else ""}!",
                    subtitle = "${primarySsh.providerName}: ${primarySsh.username}@${primarySsh.ipAddress}:${primarySsh.port} (${primarySsh.remainingValidityLabel()})",
                    flagEmoji = primarySsh.flagEmoji,
                    detailBadge = "TLS ${primarySsh.tlsPort} • UDP ${primarySsh.udpPort}",
                    tone = VpnPopupTone.CONNECTED
                )
            } else {
                _filterState.update {
                    it.copy(
                        isFetchingSshAccounts = false,
                        sshCaptchaGatedProviders = harvest.captchaGatedProviders,
                        showFreeSshFetcherModal = true
                    )
                }
            }
        }
    }

    /**
     * Imports a newly created SSH / SSH-TLS / UDP account captured via the In-App Provider Auto-Grabber
     * or pasted SSH config/URI/JSON, verifies SSH-2 handshake + Exit IP, and optionally connects right away.
     */
    fun importFreeSshAccountPayload(
        rawPayload: String,
        providerName: String = "Free SSH Provider",
        connectNow: (VpnNode) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsedList = com.example.vpn.EphemeralAccountProvisioner.parseFeedPayload(
                rawPayload = rawPayload,
                sourceName = providerName
            )
            if (parsedList.isEmpty()) {
                showEventPopup(
                    title = "Could Not Detect SSH Account",
                    subtitle = "Finish creating the free SSH account on the provider page or paste Host/User/Pass text.",
                    flagEmoji = "⚠️",
                    detailBadge = "Parser Ready",
                    tone = VpnPopupTone.INFO
                )
                return@launch
            }

            val directIp = AetherVpnStateBus.telemetry.value.deviceDirectIp
            val firstParsed = parsedList.first()
            val probe = com.example.vpn.SshTunnelManager.verifySshNodeEndToEndTls(
                node = firstParsed,
                deviceDirectIp = directIp,
                timeoutMs = 6000,
                protectSocket = AetherVpnStateBus.protectSocketCallback
            )

            val verifiedNode = if (probe.success) {
                firstParsed.copy(
                    pingMs = probe.tcpHandshakeMs.coerceAtLeast(1),
                    verifiedExitIp = probe.exitIp.orEmpty(),
                    isVerifiedWorking = true
                )
            } else {
                firstParsed
            }

            com.example.vpn.EphemeralAccountProvisioner.saveAccountToVault(getApplication(), verifiedNode)
            _customNodes.update { existing ->
                (listOf(verifiedNode) + parsedList.drop(1) + existing)
                    .distinctBy { "${it.ipAddress}:${it.port}:${it.username}" }
            }
            val merged = repository.deduplicateFastestPerCountry(_customNodes.value + _nodes.value)
            _nodes.value = merged
            AetherVpnStateBus.updateNodePool(merged)

            selectManualNode(verifiedNode)
            _filterState.update { it.copy(showFreeSshFetcherModal = false) }
            showEventPopup(
                title = "SSH Account Ready (${verifiedNode.username})",
                subtitle = "${verifiedNode.ipAddress}:${verifiedNode.port} • TLS ${verifiedNode.tlsPort} • UDP ${verifiedNode.udpPort} • ${verifiedNode.remainingValidityLabel()}",
                flagEmoji = verifiedNode.flagEmoji,
                detailBadge = if (probe.success) "SSH-2 Verified (${verifiedNode.pingMs} ms)" else "Saved to SSH Vault",
                tone = VpnPopupTone.CONNECTED
            )
            if (probe.success) {
                connectNow(verifiedNode)
            }
        }
    }

    /**
     * Triggers live internet fetch/renewal of the active/selected SSH account, or opens the
     * 1-Tap Free SSH Provider Fetcher if manual CAPTCHA completion is needed.
     */
    fun renewEphemeralSshAccountNow() {
        fetchFreeSshFromInternetNow()
    }

    fun startVpnServiceWithNode(
        context: Context,
        node: VpnNode = uiState.value.selectedNode,
        isManualSelection: Boolean = !uiState.value.isAutoFastestMode
    ) {
        val prefs = uiState.value.preferences
        val rawTargetNode = if (isManualSelection) {
            node
        } else {
            _nodes.value.filter { it.isVerifiedWorking && it.pingMs > 0 }.minByOrNull { it.pingMs }
                ?: _nodes.value.firstOrNull { it.ipAddress.isNotBlank() }
                ?: node
        }

        if (rawTargetNode.ipAddress.isBlank()) {
            fetchLatestAndConnectFastestNow { resolvedNode ->
                startVpnServiceWithNode(context, resolvedNode, isManualSelection = false)
            }
            return
        }

        viewModelScope.launch {
            val attached = com.example.vpn.EphemeralAccountProvisioner.attachCachedOrQuickAccount(
                context = context,
                node = rawTargetNode
            )
            val resolvedNode = if (attached.isSshProtocol && attached.isAccountExpired()) {
                com.example.vpn.EphemeralAccountProvisioner.ensureFreshAccountForConnection(
                    context = context,
                    node = attached,
                    customFeedUrl = prefs.sshProviderFeedUrl,
                    forceRenew = true
                )
            } else {
                attached
            }
            repository.saveLastSelectedNode(resolvedNode)

            val intent = AVpnQuickTileService.buildConnectServiceIntent(
                context = context,
                node = resolvedNode,
                isManualSelection = isManualSelection,
                dnsServer = prefs.dnsServer
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopVpnService(context: Context) {
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_DISCONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
