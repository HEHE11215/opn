package com.example.vpn

import com.example.model.VpnNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Ultra-lightweight Coroutine-based background latency worker for **A VPN** (By Ashir ♥️).
 *
 * Refactored for accurate VPN tunnel latency & minimal CPU/battery/network footprint:
 * - Performs genuine TCP 3-way handshake duration tests (`LocalTunnelGateway.measureTcpHandshakeDurationMs`)
 *   via protected sockets (`VpnService.protect`) timed with `System.nanoTime()` (no ICMP).
 * - Uses gentle scheduling (`15_000ms` default interval) and low bounded concurrency (`Semaphore(4)`)
 *   so background socket/CPU overhead stays minimal.
 * - Supports on-demand single-pass execution via [measureTcpHandshakesNow] and unit testing.
 */
class ServerLatencyWorker(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 15_000L,
    private val tcpTimeoutMs: Int = 2_000,
    private val maxConcurrentTests: Int = 4
) {
    private var workerJob: Job? = null
    private val missCounts = HashMap<String, Int>()

    fun start(
        getNodes: () -> List<VpnNode>,
        shouldSkipCycle: () -> Boolean,
        onMeasured: (updatedAliveNodes: List<VpnNode>) -> Unit
    ) {
        stop()
        workerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                if (shouldSkipCycle()) continue

                val currentList = getNodes()
                if (currentList.isEmpty()) continue

                val measured = measureTcpHandshakesOnce(currentList)
                if (isActive) {
                    onMeasured(measured)
                }
            }
        }
    }

    /**
     * Executes a single bounded pass of real TCP handshake duration tests across [nodes]
     * using [probeFn] (defaulting to [LocalTunnelGateway.measureTcpHandshakeDurationMs]).
     */
    suspend fun measureTcpHandshakesOnce(
        nodes: List<VpnNode>,
        probeFn: (String, Int, Int) -> Int? = { ip, port, timeout ->
            LocalTunnelGateway.measureTcpHandshakeDurationMs(
                ipOrHost = ip,
                port = port,
                timeoutMs = timeout,
                protectSocket = AetherVpnStateBus.protectSocketCallback
            )
        }
    ): List<VpnNode> = coroutineScope {
        if (nodes.isEmpty()) return@coroutineScope emptyList()
        val semaphore = Semaphore(maxConcurrentTests)

        nodes.map { node ->
            async(Dispatchers.IO) {
                if (node.ipAddress.isBlank() || node.port !in 1..65535) {
                    return@async null
                }
                val key = "${node.ipAddress}:${node.port}"
                semaphore.withPermit {
                    val handshakeMs = probeFn(node.ipAddress, node.port, tcpTimeoutMs)
                    when {
                        handshakeMs != null -> {
                            synchronized(missCounts) { missCounts[key] = 0 }
                            node.copy(pingMs = handshakeMs)
                        }
                        node.isCustomNode -> node.copy(pingMs = -1)
                        else -> {
                            val misses = synchronized(missCounts) {
                                val next = (missCounts[key] ?: 0) + 1
                                missCounts[key] = next
                                next
                            }
                            // Evict dead public relays after 2 consecutive missed TCP handshakes
                            if (misses >= 2) null else node
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    fun stop() {
        workerJob?.cancel()
        workerJob = null
    }
}
