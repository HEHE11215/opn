package com.example.vpn

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.model.TunnelConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile (`AVpnQuickTileService`) for **A VPN** (By Ashir ♥️).
 */
class AVpnQuickTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var telemetryJob: Job? = null

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
        val telemetry = AetherVpnStateBus.telemetry.value
        val isConnectedOrConnecting =
            telemetry.status == TunnelConnectionStatus.CONNECTED ||
                telemetry.status == TunnelConnectionStatus.CONNECTING

        if (isConnectedOrConnecting) {
            val stopIntent = Intent(this, AetherVpnService::class.java).apply {
                action = AetherVpnService.ACTION_DISCONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(stopIntent)
            } else {
                startService(stopIntent)
            }
            updateTileUi(
                state = Tile.STATE_INACTIVE,
                label = "A VPN",
                subtitle = "Disconnected"
            )
            return
        }

        val prepareIntent = try {
            VpnService.prepare(this)
        } catch (_: Exception) {
            null
        }

        val bestNode = AetherVpnStateBus.nodePool.value
            .filter { it.isVerifiedWorking && it.ipAddress.isNotBlank() }
            .minByOrNull { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }

        if (prepareIntent == null && bestNode != null) {
            val connectIntent = Intent(this, AetherVpnService::class.java).apply {
                action = AetherVpnService.ACTION_CONNECT
                putExtra(AetherVpnService.EXTRA_NODE_ID, bestNode.id)
                putExtra(AetherVpnService.EXTRA_NODE_HOSTNAME, bestNode.hostName)
                putExtra(AetherVpnService.EXTRA_NODE_IP, bestNode.ipAddress)
                putExtra(AetherVpnService.EXTRA_NODE_PORT, bestNode.port)
                putExtra(AetherVpnService.EXTRA_NODE_COUNTRY, bestNode.countryName)
                putExtra(AetherVpnService.EXTRA_NODE_COUNTRY_CODE, bestNode.countryCode)
                putExtra(AetherVpnService.EXTRA_NODE_CITY, bestNode.cityName)
                putExtra(AetherVpnService.EXTRA_NODE_FLAG, bestNode.flagEmoji)
                putExtra(AetherVpnService.EXTRA_NODE_PING, bestNode.pingMs)
                putExtra(AetherVpnService.EXTRA_NODE_OPERATOR, bestNode.operatorName)
                putExtra(AetherVpnService.EXTRA_NODE_PROTOCOL, bestNode.protocol.name)
                putExtra(AetherVpnService.EXTRA_NODE_RELAY_TYPE, bestNode.relayType.name)
                putExtra(AetherVpnService.EXTRA_NODE_USERNAME, bestNode.username)
                putExtra(AetherVpnService.EXTRA_NODE_PASSWORD, bestNode.password)
                putExtra(AetherVpnService.EXTRA_NODE_EXIT_IP, bestNode.verifiedExitIp)
                putExtra(AetherVpnService.EXTRA_NODE_VERIFIED, bestNode.isVerifiedWorking)
                putExtra(AetherVpnService.EXTRA_NODE_IS_CUSTOM, bestNode.isCustomNode)
                putExtra(AetherVpnService.EXTRA_IS_MANUAL_SELECTION, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(connectIntent)
            } else {
                startService(connectIntent)
            }
            val pingText = if (bestNode.pingMs > 0) "${bestNode.pingMs} ms" else "Live"
            updateTileUi(
                state = Tile.STATE_ACTIVE,
                label = "A VPN • ${bestNode.countryCode}",
                subtitle = "${bestNode.flagEmoji} $pingText"
            )
        } else {
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
    }

    private fun syncTileWithTelemetry() {
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
                    subtitle = "$flag $pingText Live"
                )
            }

            TunnelConnectionStatus.CONNECTING -> {
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "A VPN • Sync",
                    subtitle = "Verifying…"
                )
            }

            else -> {
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "A VPN",
                    subtitle = "Fast Connect"
                )
            }
        }
    }

    private fun updateTileUi(state: Int, label: String, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_lock_lock)
        tile.updateTile()
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
        tileScope.cancel()
        super.onDestroy()
    }
}
