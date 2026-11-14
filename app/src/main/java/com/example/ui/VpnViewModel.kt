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
 * MVVM ViewModel for **A VPN** (Crafted by Ashir ♥️) backed by strict TLS-verified remote relays
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

        val selected = if (!isAutoMode) {
            val manualOverride = filter.manualSelectedNodeOverride
            if (manualOverride != null) {
                nodes.firstOrNull {
                    it.id == manualOverride.id ||
                        (it.ipAddress == manualOverride.ipAddress && it.port == manualOverride.port)
                } ?: manualOverride
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
            activePopup = filter.activePopup
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VpnUiState(
            allNodes = emptyList(),
            filteredNodes = emptyList(),
            selectedNode = repository.getInitialBootstrapNode(),
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
                                    badgeText = "Made with love by Ashir ♥️",
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
                                    subtitle = "Tunnel closed safely • Tap orb anytime to reconnect",
                                    flagEmoji = "🛡️",
                                    detailBadge = "Standby Mode",
                                    badgeText = "Made with love by Ashir ♥️",
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
                                badgeText = "Made with love by Ashir ♥️",
                                tone = VpnPopupTone.DISCONNECTED
                            )
                        }

                        else -> {}
                    }
                    lastObservedStatus = newStatus
                }

                // If live exit GeoIP check corrected the country/flag for the connected relay, sync _nodes & manual override
                if (newStatus == TunnelConnectionStatus.CONNECTED && node != null) {
                    val existingMatch = _nodes.value.firstOrNull {
                        it.ipAddress == node.ipAddress && it.port == node.port
                    }
                    if (existingMatch != null && !existingMatch.countryCode.equals(node.countryCode, ignoreCase = true)) {
                        val updatedList = _nodes.value.map { poolNode ->
                            if (poolNode.ipAddress == node.ipAddress && poolNode.port == node.port) {
                                node.copy(pingMs = if (node.pingMs > 0) node.pingMs else poolNode.pingMs)
                            } else {
                                poolNode
                            }
                        }
                        val merged = repository.deduplicateFastestPerCountry(_customNodes.value + updatedList)
                        _nodes.value = merged
                        AetherVpnStateBus.updateNodePool(merged)
                    }
                    val currentManual = _filterState.value.manualSelectedNodeOverride
                    if (currentManual != null &&
                        currentManual.ipAddress == node.ipAddress &&
                        currentManual.port == node.port &&
                        !currentManual.countryCode.equals(node.countryCode, ignoreCase = true)
                    ) {
                        _filterState.update { it.copy(manualSelectedNodeOverride = node) }
                    }
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
                    val refreshedManual = merged.firstOrNull {
                        it.id == currentManual.id ||
                            (it.ipAddress == currentManual.ipAddress && it.port == currentManual.port)
                    }
                    if (refreshedManual != null) {
                        _filterState.update { it.copy(manualSelectedNodeOverride = refreshedManual) }
                    }
                }
            }
        )
    }

    fun showEventPopup(
        title: String,
        subtitle: String,
        flagEmoji: String = "🔥",
        detailBadge: String = "Verified Exit Tunnel",
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

            val result = repository.fetchLivePublicNodes(
                deviceDirectIp = directIp,
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
        relayType: RelayTunnelType = RelayTunnelType.SOCKS5,
        username: String = "",
        password: String = ""
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
                cityName = cityName.trim().ifEmpty { "Custom Server" },
                flagEmoji = PublicNodeRepository.countryCodeToFlagEmoji(cleanCode),
                region = PublicNodeRepository.mapCountryCodeToRegion(cleanCode),
                pingMs = -1,
                downloadSpeedMbps = 500.0,
                operatorName = operatorName.trim().ifEmpty { "Custom Server" },
                protocol = protocol,
                relayType = relayType,
                username = username.trim(),
                password = password,
                isCustomNode = true,
                isVerifiedWorking = false
            )

            val directIp = AetherVpnStateBus.telemetry.value.deviceDirectIp
            val e2eResult = LocalTunnelGateway.verifyNodeEndToEndTls(
                node = baseNode,
                deviceDirectIp = directIp,
                timeoutMs = 3500,
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
                    "$cleanIp:$safePort reachable (${customNode.pingMs} ms), TLS check pending"
                } else {
                    "$cleanIp:$safePort unreachable — check host, port, or credentials"
                },
                flagEmoji = customNode.flagEmoji,
                detailBadge = if (customNode.pingMs > 0) "⚡ ${customNode.pingMs} ms Live" else "Custom Server",
                badgeText = "Made with love by Ashir ♥️",
                tone = if (customNode.isVerifiedWorking) VpnPopupTone.CONNECTED else VpnPopupTone.INFO
            )
        }
    }

    fun startVpnServiceWithNode(
        context: Context,
        node: VpnNode = uiState.value.selectedNode,
        isManualSelection: Boolean = !uiState.value.isAutoFastestMode
    ) {
        val prefs = uiState.value.preferences
        val targetNode = if (isManualSelection) {
            node
        } else {
            _nodes.value.filter { it.isVerifiedWorking && it.pingMs > 0 }.minByOrNull { it.pingMs }
                ?: _nodes.value.firstOrNull { it.ipAddress.isNotBlank() }
                ?: node
        }

        if (targetNode.ipAddress.isBlank()) {
            fetchLatestAndConnectFastestNow { resolvedNode ->
                startVpnServiceWithNode(context, resolvedNode, isManualSelection = false)
            }
            return
        }

        viewModelScope.launch {
            repository.saveLastSelectedNode(targetNode)
        }

        val intent = AVpnQuickTileService.buildConnectServiceIntent(
            context = context,
            node = targetNode,
            isManualSelection = isManualSelection,
            dnsServer = prefs.dnsServer
        )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
