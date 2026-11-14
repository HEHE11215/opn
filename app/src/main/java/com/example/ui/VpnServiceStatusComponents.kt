package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TunnelConnectionStatus
import com.example.model.TunnelTelemetry
import com.example.model.VpnDiagnosticEntry
import com.example.model.VpnDiagnosticSeverity
import com.example.ui.theme.AmberHandshaking
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean Material 3 Visual Indicator Component reflecting the live [android.net.VpnService] state
 * (`CONNECTING`, `CONNECTED`, `DISCONNECTED`, `DISCONNECTING`, `ERROR`) alongside
 * handshake pipeline readiness badges (`FGS`, `TLS`, `TUN0`).
 */
@Composable
fun VpnServiceStateIndicatorBar(
    telemetry: TunnelTelemetry,
    isDark: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val status = telemetry.status
    val accentColor = when (status) {
        TunnelConnectionStatus.CONNECTED -> EmeraldConnected
        TunnelConnectionStatus.CONNECTING -> AmberHandshaking
        TunnelConnectionStatus.DISCONNECTING -> AmberHandshaking
        TunnelConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        TunnelConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.primary
    }

    val stateLabel = when (status) {
        TunnelConnectionStatus.CONNECTED -> "CONNECTED"
        TunnelConnectionStatus.CONNECTING -> "CONNECTING"
        TunnelConnectionStatus.DISCONNECTING -> "DISCONNECTING"
        TunnelConnectionStatus.ERROR -> "ERROR"
        TunnelConnectionStatus.DISCONNECTED -> "DISCONNECTED"
    }

    val stateSubtitle = when (status) {
        TunnelConnectionStatus.CONNECTED -> {
            val country = telemetry.verifiedExitCountry
                ?: telemetry.activeNode?.countryName
                ?: "Remote Relay"
            val exitIp = telemetry.verifiedExitIp ?: telemetry.activeNode?.ipAddress.orEmpty()
            if (exitIp.isNotBlank()) {
                "TUN0 active • $country ($exitIp)"
            } else {
                "TUN0 active • $country"
            }
        }
        TunnelConnectionStatus.CONNECTING -> telemetry.handshakeSummary.ifBlank {
            "Negotiating TCP/TLS handshake & TUN0…"
        }
        TunnelConnectionStatus.DISCONNECTING -> "Closing TUN0 interface safely…"
        TunnelConnectionStatus.ERROR -> telemetry.lastFailureStage
            ?: telemetry.handshakeSummary.ifBlank { "Tunnel handshake failed" }
        TunnelConnectionStatus.DISCONNECTED -> if (!telemetry.lastFailureStage.isNullOrBlank()) {
            "Idle • Last issue: ${telemetry.lastFailureStage}"
        } else {
            "VpnService idle • Ready to protect traffic"
        }
    }

    val errorCount = telemetry.diagnosticLogs.count {
        it.severity == VpnDiagnosticSeverity.ERROR || it.severity == VpnDiagnosticSeverity.WARNING
    }

    M3SectionCard(
        cornerRadius = 14.dp,
        borderColor = if (status != TunnelConnectionStatus.DISCONNECTED) {
            accentColor.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "VpnService state indicator: $stateLabel. $stateSubtitle"
            }
            .testTag("vpn_service_visual_indicator")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: State Beacon + Status Pill + Right-aligned [FGS][TLS][TUN0]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (status) {
                            TunnelConnectionStatus.CONNECTING,
                            TunnelConnectionStatus.DISCONNECTING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = accentColor
                                )
                            }
                            TunnelConnectionStatus.CONNECTED -> {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            TunnelConnectionStatus.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            TunnelConnectionStatus.DISCONNECTED -> {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("vpn_service_state_label")
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PipelineStepChip(
                        label = "FGS",
                        isActive = telemetry.isForegroundServiceActive,
                        isPending = status == TunnelConnectionStatus.CONNECTING && !telemetry.isForegroundServiceActive,
                        isError = false
                    )
                    PipelineStepChip(
                        label = "TLS",
                        isActive = status == TunnelConnectionStatus.CONNECTED,
                        isPending = status == TunnelConnectionStatus.CONNECTING,
                        isError = status == TunnelConnectionStatus.ERROR
                    )
                    PipelineStepChip(
                        label = "TUN0",
                        isActive = status == TunnelConnectionStatus.CONNECTED,
                        isPending = status == TunnelConnectionStatus.CONNECTING ||
                            status == TunnelConnectionStatus.DISCONNECTING,
                        isError = status == TunnelConnectionStatus.ERROR &&
                            telemetry.lastFailureStage?.contains("TUN", ignoreCase = true) == true
                    )
                }
            }

            // Row 2: Subtitle + Non-wrapping Alert Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stateSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (errorCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "$errorCount alert${if (errorCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineStepChip(
    label: String,
    isActive: Boolean,
    isPending: Boolean,
    isError: Boolean
) {
    val chipColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> EmeraldConnected
        isPending -> AmberHandshaking
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive || isPending || isError) {
            chipColor.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp
            ),
            color = chipColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * Clean Material 3 Diagnostic Display capturing specific connection errors, TCP timeouts,
 * SOCKS5/HTTP handshake rejections, TLS verification errors, TUN0 setup errors, and failover traces.
 */
@Composable
fun VpnDiagnosticPanel(
    telemetry: TunnelTelemetry,
    isDark: Boolean = false,
    onClearDiagnostics: () -> Unit,
    onRetryFastSwitch: () -> Unit,
    onBrowseServers: () -> Unit,
    onOpenFullLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasFailure = telemetry.status == TunnelConnectionStatus.ERROR ||
        !telemetry.lastFailureReason.isNullOrBlank() ||
        telemetry.diagnosticLogs.any {
            it.severity == VpnDiagnosticSeverity.ERROR || it.severity == VpnDiagnosticSeverity.WARNING
        }

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val showExpandedList = isExpanded || telemetry.status == TunnelConnectionStatus.ERROR

    val logs = telemetry.diagnosticLogs.asReversed()
    val errorLogsCount = telemetry.diagnosticLogs.count {
        it.severity == VpnDiagnosticSeverity.ERROR || it.severity == VpnDiagnosticSeverity.WARNING
    }

    val panelAccent = when {
        telemetry.status == TunnelConnectionStatus.ERROR -> RoseAlert
        errorLogsCount > 0 -> AmberHandshaking
        telemetry.status == TunnelConnectionStatus.CONNECTED -> EmeraldConnected
        else -> MaterialTheme.colorScheme.primary
    }

    M3SectionCard(
        cornerRadius = 16.dp,
        borderColor = panelAccent.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("vpn_diagnostic_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { isExpanded = !isExpanded }
                    .testTag("vpn_diagnostic_expand_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = panelAccent.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasFailure) Icons.Default.WarningAmber else Icons.Default.BugReport,
                            contentDescription = "Tunnel diagnostics icon",
                            tint = panelAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Handshake Diagnostics",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = panelAccent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = if (errorLogsCount > 0) {
                                    "$errorLogsCount issue${if (errorLogsCount == 1) "" else "s"}"
                                } else {
                                    "${logs.size} event${if (logs.size == 1) "" else "s"}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = panelAccent,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (hasFailure) {
                            "Specific VpnService failure reasons captured live"
                        } else {
                            "Inspect TCP, TLS, TUN0 & failover trace from VpnService"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty() || !telemetry.lastFailureReason.isNullOrBlank()) {
                        IconButton(
                            onClick = onClearDiagnostics,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("vpn_diagnostic_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear diagnostic logs",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (showExpandedList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showExpandedList) "Collapse diagnostics" else "Expand diagnostics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            val failureReason = telemetry.errorMessage ?: telemetry.lastFailureReason
            if (!failureReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val isHardError = telemetry.status == TunnelConnectionStatus.ERROR
                val calloutColor = if (isHardError) RoseAlert else AmberHandshaking
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = calloutColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, calloutColor.copy(alpha = 0.40f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vpn_diagnostic_failure_callout")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isHardError) Icons.Default.ErrorOutline else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = calloutColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = telemetry.lastFailureStage ?: "Handshake Failure",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                color = calloutColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            telemetry.lastFailedServerLabel?.let { srv ->
                                Text(
                                    text = "• $srv",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = failureReason,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onRetryFastSwitch,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = calloutColor
                                ),
                                border = BorderStroke(1.dp, calloutColor.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("vpn_diagnostic_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Try Fastest",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            OutlinedButton(
                                onClick = onBrowseServers,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("vpn_diagnostic_servers_button")
                            ) {
                                Text(
                                    text = "Switch Server",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenFullLogs,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("vpn_diagnostic_open_log_button")
                        ) {
                            Text(
                                text = "Open Full Connection Log",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Direct conditional rendering without expand/shrink animations
            if (showExpandedList) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    if (logs.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "No tunnel errors recorded. Connect to capture live TCP, SOCKS5/HTTP, TLS, and TUN0 handshake events.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        logs.take(6).forEach { entry ->
                            DiagnosticLogRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogRow(
    entry: VpnDiagnosticEntry
) {
    val severityColor = when (entry.severity) {
        VpnDiagnosticSeverity.SUCCESS -> EmeraldConnected
        VpnDiagnosticSeverity.WARNING -> AmberHandshaking
        VpnDiagnosticSeverity.ERROR -> RoseAlert
        VpnDiagnosticSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val severityIcon = when (entry.severity) {
        VpnDiagnosticSeverity.SUCCESS -> Icons.Default.CheckCircle
        VpnDiagnosticSeverity.WARNING -> Icons.Default.WarningAmber
        VpnDiagnosticSeverity.ERROR -> Icons.Default.ErrorOutline
        VpnDiagnosticSeverity.INFO -> Icons.Default.Sync
    }

    val formattedTime = remember(entry.timestampEpochMs) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestampEpochMs))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vpn_diagnostic_log_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = severityIcon,
                contentDescription = entry.severity.name,
                tint = severityColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(15.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${entry.phase} • ${entry.summary}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = severityColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = entry.serverLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
