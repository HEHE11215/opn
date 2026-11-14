package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.model.VpnDiagnosticSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager [CoroutineWorker] for battery-optimized background TCP handshake latency checks.
 *
 * When the user enables Wi-Fi Only battery optimization, WorkManager enforces
 * [NetworkType.UNMETERED] + `setRequiresBatteryNotLow(true)` so cellular radios stay idle.
 */
class LatencyCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val wifiOnly = inputData.getBoolean(BackgroundLatencyWorkScheduler.KEY_WIFI_ONLY, false)
        if (wifiOnly && BackgroundLatencyWorkScheduler.isRestrictedByWifiOnly(applicationContext, true)) {
            AetherVpnStateBus.recordDiagnostic(
                phase = "WorkManager Battery Saver",
                serverLabel = "Background Scheduler",
                summary = "Deferred latency check (Wi-Fi Only active)",
                detail = "Active connection is metered/non-Wi-Fi; waiting for unmetered Wi-Fi.",
                severity = VpnDiagnosticSeverity.INFO
            )
            return@withContext Result.success()
        }

        val currentPool = AetherVpnStateBus.nodePool.value
        if (currentPool.isEmpty()) {
            return@withContext Result.success()
        }

        // Probe up to 20 pooled nodes with bounded concurrency (4 sockets max) for ultra-low power use
        val targetSlice = currentPool.take(20)
        val semaphore = Semaphore(4)
        val protectSocket = AetherVpnStateBus.protectSocketCallback

        val measuredList = coroutineScope {
            targetSlice.map { node ->
                async(Dispatchers.IO) {
                    if (node.ipAddress.isBlank() || node.port !in 1..65535) {
                        return@async node
                    }
                    semaphore.withPermit {
                        val handshakeMs = LocalTunnelGateway.measureTcpHandshakeDurationMs(
                            ipOrHost = node.ipAddress,
                            port = node.port,
                            timeoutMs = 2_000,
                            protectSocket = protectSocket
                        )
                        if (handshakeMs != null) node.copy(pingMs = handshakeMs) else node
                    }
                }
            }.awaitAll()
        }

        val measuredById = measuredList.associateBy { "${it.ipAddress}:${it.port}" }
        val updatedPool = currentPool.map { node ->
            measuredById["${node.ipAddress}:${node.port}"] ?: node
        }
        AetherVpnStateBus.updateNodePool(updatedPool)

        val liveCount = measuredList.count { it.pingMs > 0 }
        AetherVpnStateBus.recordDiagnostic(
            phase = "WorkManager Latency Sync",
            serverLabel = if (wifiOnly) "Unmetered Wi-Fi Policy" else "All-Network Policy",
            summary = "Measured $liveCount/${targetSlice.size} relays via TCP handshake",
            detail = if (wifiOnly) {
                "Executed under WorkManager NetworkType.UNMETERED + battery-not-low constraint."
            } else {
                "Executed under WorkManager NetworkType.CONNECTED constraint."
            },
            severity = VpnDiagnosticSeverity.INFO
        )

        Result.success()
    }
}

/**
 * Configures and updates the unique periodic [WorkManager] latency schedule
 * plus lightweight network checks for live foreground polling.
 */
object BackgroundLatencyWorkScheduler {

    const val UNIQUE_WORK_NAME = "avpn_background_latency_check"
    const val KEY_WIFI_ONLY = "key_wifi_only"
    const val TAG_WIFI_ONLY = "latency_policy_wifi_only"
    const val TAG_ALL_NETWORKS = "latency_policy_all_networks"

    fun buildConstraints(wifiOnly: Boolean): Constraints {
        val builder = Constraints.Builder()
        if (wifiOnly) {
            builder
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
        } else {
            builder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        return builder.build()
    }

    fun buildPeriodicRequest(wifiOnly: Boolean): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<LatencyCheckWorker>(
            15L,
            TimeUnit.MINUTES
        )
            .setConstraints(buildConstraints(wifiOnly))
            .setInputData(workDataOf(KEY_WIFI_ONLY to wifiOnly))
            .addTag(if (wifiOnly) TAG_WIFI_ONLY else TAG_ALL_NETWORKS)
            .build()
    }

    fun scheduleOrUpdate(context: Context, wifiOnly: Boolean) {
        runCatching {
            val request = buildPeriodicRequest(wifiOnly)
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    /**
     * Returns true when [wifiOnly] is enabled AND the current active network is metered or not Wi-Fi,
     * allowing [ServerLatencyWorker] to skip cellular/metered background TCP probes to save power.
     */
    fun isRestrictedByWifiOnly(context: Context, wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return false
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching false
            val activeNetwork = cm.activeNetwork ?: return@runCatching true
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching true
            val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
                !cm.isActiveNetworkMetered
            !(hasWifi || isUnmetered)
        }.getOrDefault(false)
    }
}
