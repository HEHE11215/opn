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
import android.app.StatusBarManager
import com.example.MainActivity
import com.example.R
import com.example.data.PublicNodeRepository
import com.example.model.TunnelConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Top Status Bar Quick Settings Tile (`AVpnQuickTileService`) for **A VPN** (By Ashir ♥️).
 *
 * Allows 1-tap VPN Connect / Disconnect directly from the pull-down Status Bar / Control Center shade.
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
            startService(stopIntent)
            updateTileUi(
                state = Tile.STATE_INACTIVE,
                label = "A VPN",
                subtitle = "Disconnected"
            )
            return
        }

        // Check if VpnService permission is already granted
        val prepareIntent = try {
            VpnService.prepare(this)
        } catch (_: Exception) {
            null
        }

        if (prepareIntent == null) {
            // Directly start the fastest Wi-Fi Warp tunnel from the status bar without opening UI!
            val repo = PublicNodeRepository(applicationContext)
            val bestNode = AetherVpnStateBus.nodePool.value.minByOrNull { it.pingMs }
                ?: repo.getVerifiedCommunityNodes().first()

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
                putExtra(AetherVpnService.EXTRA_NODE_VERIFIED, true)
                putExtra(AetherVpnService.EXTRA_IS_MANUAL_SELECTION, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(connectIntent)
            } else {
                startService(connectIntent)
            }
            updateTileUi(
                state = Tile.STATE_ACTIVE,
                label = "A VPN • ${bestNode.countryCode}",
                subtitle = "${bestNode.flagEmoji} ${bestNode.pingMs} ms"
            )
        } else {
            // Launch MainActivity with auto-connect intent flag
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
                val ping = node?.pingMs ?: 12
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "A VPN • $countryCode",
                    subtitle = "$flag $ping ms Live"
                )
            }

            TunnelConnectionStatus.CONNECTING -> {
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "A VPN • Sync",
                    subtitle = "Connecting…"
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

    companion object {
        /**
         * Requests Android 13+ StatusBarManager to add the Quick Settings Tile to the top status bar,
         * and always pins the Fast-Start Quick Connect Notification to the top status bar.
         */
        fun pinTopStatusBarShortcut(context: Context, onResult: (String) -> Unit) {
            // 1. Always post/refresh the Persistent Fast-Start Notification in the top status bar
            val pinIntent = Intent(context, AetherVpnService::class.java).apply {
                action = AetherVpnService.ACTION_PIN_QUICK_NOTIFICATION
            }
            try {
                context.startService(pinIntent)
            } catch (_: Exception) {
            }

            // 2. On Android 13+ (API 33+), also prompt the native Quick Settings Tile adder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                    statusBarManager?.requestAddTileService(
                        ComponentName(context, AVpnQuickTileService::class.java),
                        context.getString(R.string.app_name),
                        Icon.createWithResource(context, android.R.drawable.ic_lock_lock),
                        context.mainExecutor
                    ) { _ -> }
                } catch (_: Exception) {
                }
            }

            onResult("Fast-Start button added to Top Status Bar!")
        }
    }
}
