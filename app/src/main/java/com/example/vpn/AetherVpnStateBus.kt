package com.example.vpn

import com.example.model.TunnelTelemetry
import com.example.model.VpnDiagnosticEntry
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.DatagramSocket
import java.net.Socket

/**
 * Lightweight reactive state bus bridging [AetherVpnService] with the Jetpack Compose UI.
 */
object AetherVpnStateBus {

    private const val MAX_DIAGNOSTIC_LOGS = 40

    /**
     * Registered by [AetherVpnService] while active so outgoing sockets and UDP DNS probes
     * bypass `tun0` (`VpnService.protect`) and never create a routing loop.
     */
    @Volatile
    var protectSocketCallback: ((Socket) -> Boolean)? = null

    @Volatile
    var protectDatagramCallback: ((DatagramSocket) -> Boolean)? = null

    private val _telemetry = MutableStateFlow(TunnelTelemetry())
    val telemetry: StateFlow<TunnelTelemetry> = _telemetry.asStateFlow()

    /**
     * Deduplicated per-country server list displayed in the UI.
     */
    private val _nodePool = MutableStateFlow<List<VpnNode>>(emptyList())
    val nodePool: StateFlow<List<VpnNode>> = _nodePool.asStateFlow()

    /**
     * Full non-deduplicated pool of ALL TLS-verified working relays across every country,
     * used for instant same-country and global failover without stuttering.
     */
    private val _verifiedBackupPool = MutableStateFlow<List<VpnNode>>(emptyList())
    val verifiedBackupPool: StateFlow<List<VpnNode>> = _verifiedBackupPool.asStateFlow()

    fun updateTelemetry(transform: (TunnelTelemetry) -> TunnelTelemetry) {
        _telemetry.update(transform)
    }

    fun recordDiagnostic(
        phase: String,
        serverLabel: String,
        summary: String,
        detail: String,
        severity: VpnDiagnosticSeverity
    ) {
        val entry = VpnDiagnosticEntry(
            phase = phase,
            serverLabel = serverLabel,
            summary = summary,
            detail = detail,
            severity = severity
        )
        val isFailure = severity == VpnDiagnosticSeverity.ERROR || severity == VpnDiagnosticSeverity.WARNING
        _telemetry.update { prev ->
            val nextLogs = (prev.diagnosticLogs + entry).takeLast(MAX_DIAGNOSTIC_LOGS)
            prev.copy(
                diagnosticLogs = nextLogs,
                lastFailureStage = if (isFailure) phase else prev.lastFailureStage,
                lastFailureReason = if (isFailure) "$summary: $detail" else prev.lastFailureReason,
                lastFailedServerLabel = if (isFailure) serverLabel else prev.lastFailedServerLabel
            )
        }
    }

    fun clearDiagnostics() {
        _telemetry.update { prev ->
            prev.copy(
                diagnosticLogs = emptyList(),
                lastFailureStage = null,
                lastFailureReason = null,
                lastFailedServerLabel = null
            )
        }
    }

    fun updateNodePool(nodes: List<VpnNode>) {
        _nodePool.value = nodes
    }

    fun updateVerifiedBackupPool(nodes: List<VpnNode>) {
        _verifiedBackupPool.value = nodes
    }
}
