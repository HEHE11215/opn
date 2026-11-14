package com.example.vpn

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.data.PublicNodeRepository
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Notification Shade Quick Settings Tile (`AVpnQuickTileService`) for **A VPN** (By Ashir ♥️).
 *
 * Allows users to toggle the VPN tunnel directly from the Quick Settings shade without opening the app:
 * - If CONNECTED / CONNECTING -> sends [AetherVpnService.ACTION_DISCONNECT] immediately.
 * - If DISCONNECTED / ERROR and Android VPN consent is granted -> resolves the last selected server
 *   (or fastest live verified relay), starts [AetherVpnService] directly in foreground, and updates
 *   tile state/subtitle without launching MainActivity.
 * - Only opens MainActivity when Android OS VPN permission consent (`VpnService.prepare`) is needed.
 */
class AVpnQuickTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telemetryJob: Job? = null
    private var clickJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        syncTileWithTelemetry()
    }

    override fun onStartListening() {
        super.onStartListening()
        syncTileWithTelemetry()
        telemetryJob?.cancel()
        telemetryJob = tileScope.launch {
            AetherVpnStateBus.telemetry.collect {
                syncTileWithTelemetry()
            }
        }
    }

    override fun onStopListening() {
        telemetryJob?.cancel()
        telemetryJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        toggleTunnelFromTile()
    }

    internal fun toggleTunnelFromTile(
        prepareIntentProvider: () -> Intent? = {
            try {
                VpnService.prepare(this)
            } catch (_: Exception) {
                null
            }
        }
    ) {
        val telemetry = AetherVpnStateBus.telemetry.value
        val isConnectedOrConnecting =
            telemetry.status == TunnelConnectionStatus.CONNECTED ||
                telemetry.status == TunnelConnectionStatus.CONNECTING

        if (isConnectedOrConnecting) {
            val stopIntent = Intent(this, AetherVpnService::class.java).apply {
                action = AetherVpnService.ACTION_DISCONNECT
            }
            dispatchServiceIntent(stopIntent)
            AetherVpnStateBus.recordDiagnostic(
                phase = "Quick Settings Tile",
                serverLabel = telemetry.activeNode?.let { "${it.flagEmoji} ${it.countryName}" } ?: "TUN0",
                summary = "Disconnected from Notification Shade",
                detail = "User toggled Quick Settings tile to OFF without opening app.",
                severity = VpnDiagnosticSeverity.INFO
            )
            updateTileUi(
                state = Tile.STATE_INACTIVE,
                label = "A VPN",
                subtitle = "Disconnected"
            )
            return
        }

        val prepareIntent = prepareIntentProvider()
        if (prepareIntent != null) {
            launchAppForVpnConsent()
            return
        }

        clickJob?.cancel()
        clickJob = tileScope.launch {
            val repository = PublicNodeRepository(applicationContext)
            val resolved = withContext(Dispatchers.IO) {
                val prefs = runCatching { repository.preferencesFlow.first() }.getOrNull()
                val cachedTarget = resolveCachedTileTarget(
                    poolNodes = AetherVpnStateBus.nodePool.value,
                    savedNodeId = prefs?.selectedNodeId ?: "auto_fastest",
                    isAutoSelectFastest = prefs?.autoSelectFastest ?: true,
                    lastSavedNode = prefs?.lastSelectedNode,
                    fgsRestored = AetherVpnService.loadPersistedRestoreConfig(applicationContext)
                )
                if (cachedTarget != null) {
                    Triple(cachedTarget.first, cachedTarget.second, prefs?.dnsServer ?: "1.1.1.1")
                } else {
                    withContext(Dispatchers.Main.immediate) {
                        updateTileUi(
                            state = Tile.STATE_ACTIVE,
                            label = "A VPN • Sync",
                            subtitle = "Finding relay…"
                        )
                    }
                    val fetched = repository.fetchLivePublicNodes().getOrNull().orEmpty()
                    if (fetched.isNotEmpty()) {
                        AetherVpnStateBus.updateNodePool(fetched)
                    }
                    val fastest = fetched.filter { it.isVerifiedWorking && it.ipAddress.isNotBlank() }
                        .minByOrNull { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                        ?: fetched.firstOrNull { it.ipAddress.isNotBlank() }
                    fastest?.let { node ->
                        repository.saveLastSelectedNode(node)
                        Triple(node, false, prefs?.dnsServer ?: "1.1.1.1")
                    }
                }
            }

            if (resolved != null) {
                val (targetNode, isManual, dnsServer) = resolved
                val connectIntent = buildConnectServiceIntent(
                    context = this@AVpnQuickTileService,
                    node = targetNode,
                    isManualSelection = isManual,
                    dnsServer = dnsServer
                )
                dispatchServiceIntent(connectIntent)
                AetherVpnStateBus.recordDiagnostic(
                    phase = "Quick Settings Tile",
                    serverLabel = "${targetNode.flagEmoji} ${targetNode.countryName}",
                    summary = "Started VPN from Notification Shade",
                    detail = "Toggled tile ON directly in background (${targetNode.ipAddress}:${targetNode.port}).",
                    severity = VpnDiagnosticSeverity.INFO
                )
                val pingText = if (targetNode.pingMs > 0) "${targetNode.pingMs} ms" else "Connecting…"
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "A VPN • ${targetNode.countryCode}",
                    subtitle = "${targetNode.flagEmoji} $pingText"
                )
            } else {
                AetherVpnStateBus.recordDiagnostic(
                    phase = "Quick Settings Tile",
                    serverLabel = "No Relay Ready",
                    summary = "No verified relay found in shade toggle",
                    detail = "Open A VPN once on an active connection to refresh public relays.",
                    severity = VpnDiagnosticSeverity.WARNING
                )
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "A VPN",
                    subtitle = "No relay online"
                )
            }
        }
    }

    private fun launchAppForVpnConsent() {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_FAST_START_CONNECT, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                205,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(activityIntent)
        }
    }

    private fun dispatchServiceIntent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    internal fun syncTileWithTelemetry() {
        val tel = AetherVpnStateBus.telemetry.value
        val node = tel.activeNode
        when (tel.status) {
            TunnelConnectionStatus.CONNECTED -> {
                val countryCode = node?.countryCode ?: "ON"
                val flag = node?.flagEmoji ?: "🔥"
                val pingText = if ((node?.pingMs ?: -1) > 0) "${node?.pingMs} ms" else "Live"
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "A VPN • $countryCode",
                    subtitle = "$flag $pingText"
                )
            }

            TunnelConnectionStatus.CONNECTING -> {
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "A VPN • Sync",
                    subtitle = "Verifying…"
                )
            }

            TunnelConnectionStatus.DISCONNECTING -> {
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "A VPN",
                    subtitle = "Stopping…"
                )
            }

            else -> {
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "A VPN",
                    subtitle = "Tap to Connect"
                )
            }
        }
    }

    internal var overrideQsTileForTest: Tile? = null

    private fun updateTileUi(state: Int, label: String, subtitle: String) {
        val tile = overrideQsTileForTest ?: qsTile ?: return
        tile.state = state
        tile.label = label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.contentDescription = "$label, $subtitle"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_avpn_tile)
        runCatching { tile.updateTile() }
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
        clickJob?.cancel()
        tileScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestTileRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, AVpnQuickTileService::class.java)
                )
            }
        }

        fun buildConnectServiceIntent(
            context: Context,
            node: VpnNode,
            isManualSelection: Boolean,
            dnsServer: String = "1.1.1.1"
        ): Intent {
            return Intent(context, AetherVpnService::class.java).apply {
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
                putExtra(AetherVpnService.EXTRA_NODE_RELAY_TYPE, node.relayType.name)
                putExtra(AetherVpnService.EXTRA_NODE_USERNAME, node.username)
                putExtra(AetherVpnService.EXTRA_NODE_PASSWORD, node.password)
                putExtra(AetherVpnService.EXTRA_NODE_EXIT_IP, node.verifiedExitIp)
                putExtra(AetherVpnService.EXTRA_NODE_VERIFIED, node.isVerifiedWorking)
                putExtra(AetherVpnService.EXTRA_NODE_IS_CUSTOM, node.isCustomNode)
                putExtra(AetherVpnService.EXTRA_IS_MANUAL_SELECTION, isManualSelection)
                putExtra(AetherVpnService.EXTRA_DNS_SERVER, dnsServer)
            }
        }

        /**
         * Resolves the best node from live in-memory pool, persisted DataStore preference, or
         * Foreground Service session backup without requiring the UI Activity to be open.
         */
        internal fun resolveCachedTileTarget(
            poolNodes: List<VpnNode>,
            savedNodeId: String,
            isAutoSelectFastest: Boolean,
            lastSavedNode: VpnNode?,
            fgsRestored: Triple<VpnNode, Boolean, String>?
        ): Pair<VpnNode, Boolean>? {
            val validPool = poolNodes.filter { it.ipAddress.isNotBlank() && it.port in 1..65535 }
            if (!isAutoSelectFastest) {
                val matchedPool = validPool.firstOrNull {
                    it.id == savedNodeId ||
                        it.countryCode.equals(savedNodeId.removePrefix("country-"), ignoreCase = true)
                }
                if (matchedPool != null) return matchedPool to true
                if (lastSavedNode != null && lastSavedNode.ipAddress.isNotBlank()) {
                    return lastSavedNode to true
                }
            }

            val fastestPool = validPool
                .filter { it.isVerifiedWorking }
                .minByOrNull { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
                ?: validPool.minByOrNull { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }

            if (fastestPool != null) {
                return fastestPool to !isAutoSelectFastest
            }
            if (lastSavedNode != null && lastSavedNode.ipAddress.isNotBlank()) {
                return lastSavedNode to !isAutoSelectFastest
            }
            if (fgsRestored != null && fgsRestored.first.ipAddress.isNotBlank()) {
                return fgsRestored.first to fgsRestored.second
            }
            return null
        }
    }
}
