package com.example.vpn

import com.example.model.AuditLogEntry
import com.example.model.TunnelTelemetry
import com.example.model.VpnNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Reactive state bus that bridges the Android system-managed [AetherVpnService]
 * with the Jetpack Compose UI layer ([com.example.ui.VpnViewModel]).
 */
object AetherVpnStateBus {

    private val logIdGenerator = AtomicLong(1L)

    private val _telemetry = MutableStateFlow(TunnelTelemetry())
    val telemetry: StateFlow<TunnelTelemetry> = _telemetry.asStateFlow()

    private val _nodePool = MutableStateFlow<List<VpnNode>>(emptyList())
    val nodePool: StateFlow<List<VpnNode>> = _nodePool.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(
        listOf(
            AuditLogEntry(
                id = logIdGenerator.getAndIncrement(),
                timestampFormatted = currentTimeFormatted(),
                tag = "INIT",
                message = "AetherNode Open-Source Core initialized (SOCKS5 / HTTP-CONNECT / TUN0 Gateway ready).",
                isHighlight = true
            )
        )
    )
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    fun updateTelemetry(transform: (TunnelTelemetry) -> TunnelTelemetry) {
        _telemetry.update(transform)
    }

    fun updateNodePool(nodes: List<VpnNode>) {
        _nodePool.value = nodes
    }

    fun appendLog(tag: String, message: String, isHighlight: Boolean = false) {
        val entry = AuditLogEntry(
            id = logIdGenerator.getAndIncrement(),
            timestampFormatted = currentTimeFormatted(),
            tag = tag,
            message = message,
            isHighlight = isHighlight
        )
        _auditLogs.update { existing ->
            (listOf(entry) + existing).take(120)
        }
    }

    fun clearLogs() {
        _auditLogs.value = emptyList()
        appendLog("AUDIT", "Audit log buffer cleared by user.", isHighlight = false)
    }

    private fun currentTimeFormatted(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date())
    }
}
