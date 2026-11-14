package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

private enum class LogFilterMode(val label: String) {
    ALL("All"),
    ERRORS("Errors"),
    WARNINGS("Warnings"),
    SUCCESS("Success")
}

/**
 * Clean Material 3 **Connection Log** screen displaying verbose error messages, tunnel handshake phases,
 * TCP/TLS/Exit-IP telemetry, and troubleshooting actions pulled directly from [com.example.vpn.AetherVpnService].
 */
@Composable
fun ConnectionLogScreen(
    telemetry: TunnelTelemetry,
    isDark: Boolean = false,
    onClearLogs: () -> Unit,
    onRetryFastSwitch: () -> Unit,
    onBrowseServers: () -> Unit,
    onCopiedNotice: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filterMode by rememberSaveable { mutableStateOf(LogFilterMode.ALL) }

    val allLogsReversed = remember(telemetry.diagnosticLogs) {
        telemetry.diagnosticLogs.asReversed()
    }
    val filteredLogs = remember(allLogsReversed, filterMode) {
        when (filterMode) {
            LogFilterMode.ALL -> allLogsReversed
            LogFilterMode.ERRORS -> allLogsReversed.filter { it.severity == VpnDiagnosticSeverity.ERROR }
            LogFilterMode.WARNINGS -> allLogsReversed.filter {
                it.severity == VpnDiagnosticSeverity.WARNING || it.severity == VpnDiagnosticSeverity.ERROR
            }
            LogFilterMode.SUCCESS -> allLogsReversed.filter { it.severity == VpnDiagnosticSeverity.SUCCESS }
        }
    }

    val errorCount = remember(telemetry.diagnosticLogs) {
        telemetry.diagnosticLogs.count { it.severity == VpnDiagnosticSeverity.ERROR }
    }
    val warnCount = remember(telemetry.diagnosticLogs) {
        telemetry.diagnosticLogs.count { it.severity == VpnDiagnosticSeverity.WARNING }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("connection_log_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Live Handshake Status Summary Header Card
        item(key = "connection_log_handshake_header") {
            LiveHandshakeStatusHeaderCard(
                telemetry = telemetry,
                errorCount = errorCount,
                warnCount = warnCount,
                onCopyAll = {
                    val reportText = buildVerboseDiagnosticReport(telemetry, allLogsReversed)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("A VPN Connection Log", reportText))
                    onCopiedNotice("Copied ${allLogsReversed.size} verbose handshake events")
                },
                onClearLogs = onClearLogs
            )
        }

        // 2. Verbose Error Troubleshooting Card
        val verboseError = telemetry.errorMessage ?: telemetry.lastFailureReason
        if (!verboseError.isNullOrBlank()) {
            item(key = "connection_log_error_troubleshoot") {
                VerboseErrorTroubleshootingCard(
                    telemetry = telemetry,
                    verboseError = verboseError,
                    onRetryFastSwitch = onRetryFastSwitch,
                    onBrowseServers = onBrowseServers
                )
            }
        }

        // 3. Severity Filter Bar
        item(key = "connection_log_filter_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogFilterMode.entries.forEach { mode ->
                    val selected = filterMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { filterMode = mode },
                        label = {
                            val countSuffix = when (mode) {
                                LogFilterMode.ALL -> " (${allLogsReversed.size})"
                                LogFilterMode.ERRORS -> if (errorCount > 0) " ($errorCount)" else ""
                                LogFilterMode.WARNINGS -> if (warnCount > 0) " ($warnCount)" else ""
                                LogFilterMode.SUCCESS -> ""
                            }
                            Text(
                                text = "${mode.label}$countSuffix",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("log_filter_${mode.name.lowercase(Locale.US)}")
                    )
                }
            }
        }

        // 4. Verbose Log Stream or Empty State
        if (filteredLogs.isEmpty()) {
            item(key = "connection_log_empty_state") {
                M3SectionCard(
                    cornerRadius = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .testTag("connection_log_empty_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "No Handshake Events Recorded",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect to any relay to stream verbose TCP 3-way handshakes, SOCKS5/HTTPS probes, TLS CA checks, TUN0 allocation, and server failure diagnostics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredLogs,
                key = { "${it.id}_${it.timestampEpochMs}" }
            ) { entry ->
                VerboseConnectionLogCard(
                    entry = entry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveHandshakeStatusHeaderCard(
    telemetry: TunnelTelemetry,
    errorCount: Int,
    warnCount: Int,
    onCopyAll: () -> Unit,
    onClearLogs: () -> Unit
) {
    val accent = when (telemetry.status) {
        TunnelConnectionStatus.CONNECTED -> EmeraldConnected
        TunnelConnectionStatus.CONNECTING -> AmberHandshaking
        TunnelConnectionStatus.ERROR -> RoseAlert
        else -> MaterialTheme.colorScheme.primary
    }

    M3SectionCard(
        cornerRadius = 16.dp,
        borderColor = accent.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("connection_log_status_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "VpnService Handshake Telemetry",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VpnService Connection Log",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = telemetry.handshakeSummary.ifBlank { "Idle • Standby" },
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val targetNode = telemetry.activeNode
            val targetText = if (targetNode != null && targetNode.ipAddress.isNotBlank()) {
                "${targetNode.flagEmoji} ${targetNode.countryName} (${targetNode.ipAddress}:${targetNode.port})"
            } else {
                "No active relay"
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TelemetrySpecRow(
                    label = "State",
                    value = "${telemetry.status.name} • FGS ${if (telemetry.isForegroundServiceActive) "Active (specialUse)" else "Standby"}",
                    valueColor = accent
                )
                TelemetrySpecRow(
                    label = "Endpoint",
                    value = targetText,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                TelemetrySpecRow(
                    label = "Tunnel",
                    value = "TUN0 ${telemetry.virtualIpAddress} • MTU ${telemetry.activeMtu} • DNS ${telemetry.activeDnsServer}",
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!telemetry.verifiedExitIp.isNullOrBlank()) {
                    TelemetrySpecRow(
                        label = "Exit IP",
                        value = "${telemetry.verifiedExitIp} (${telemetry.verifiedExitCountry ?: "Remote"})",
                        valueColor = EmeraldConnected
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyAll,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("connection_log_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy Log",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    enabled = telemetry.diagnosticLogs.isNotEmpty() || !telemetry.lastFailureReason.isNullOrBlank(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (errorCount + warnCount > 0) RoseAlert else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("connection_log_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear Log",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetrySpecRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VerboseErrorTroubleshootingCard(
    telemetry: TunnelTelemetry,
    verboseError: String,
    onRetryFastSwitch: () -> Unit,
    onBrowseServers: () -> Unit
) {
    val isHardError = telemetry.status == TunnelConnectionStatus.ERROR
    val toneColor = if (isHardError) RoseAlert else AmberHandshaking
    val stage = telemetry.lastFailureStage ?: "Handshake Failure"
    val hint = remember(stage) { troubleshootHintForStage(stage) }

    M3SectionCard(
        cornerRadius = 16.dp,
        borderColor = toneColor.copy(alpha = 0.50f),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("connection_log_error_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isHardError) Icons.Default.ErrorOutline else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = toneColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stage,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = toneColor,
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

            Text(
                text = verboseError,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRetryFastSwitch,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = toneColor),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("connection_log_retry_fastest")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Try Fastest",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(
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
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("connection_log_browse_servers")
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pick Server",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun VerboseConnectionLogCard(
    entry: VpnDiagnosticEntry,
    modifier: Modifier = Modifier
) {
    val severityColor = when (entry.severity) {
        VpnDiagnosticSeverity.SUCCESS -> EmeraldConnected
        VpnDiagnosticSeverity.WARNING -> AmberHandshaking
        VpnDiagnosticSeverity.ERROR -> RoseAlert
        VpnDiagnosticSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val severityLabel = when (entry.severity) {
        VpnDiagnosticSeverity.SUCCESS -> "OK"
        VpnDiagnosticSeverity.WARNING -> "WARN"
        VpnDiagnosticSeverity.ERROR -> "ERROR"
        VpnDiagnosticSeverity.INFO -> "INFO"
    }
    val severityIcon = when (entry.severity) {
        VpnDiagnosticSeverity.SUCCESS -> Icons.Default.CheckCircle
        VpnDiagnosticSeverity.WARNING -> Icons.Default.WarningAmber
        VpnDiagnosticSeverity.ERROR -> Icons.Default.ErrorOutline
        VpnDiagnosticSeverity.INFO -> Icons.Default.Sync
    }

    val formattedTime = remember(entry.timestampEpochMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampEpochMs))
    }

    M3SectionCard(
        cornerRadius = 14.dp,
        borderColor = if (entry.severity == VpnDiagnosticSeverity.ERROR) {
            RoseAlert.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        modifier = modifier.testTag("connection_log_entry_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = severityColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = severityIcon,
                            contentDescription = severityLabel,
                            tint = severityColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = severityLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = severityColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = entry.phase,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

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
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = entry.summary,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = severityColor
            )

            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun troubleshootHintForStage(stage: String): String {
    val s = stage.lowercase(Locale.US)
    return when {
        s.contains("timeout") -> "Why this happens: The remote server did not answer TCP SYN packets within the deadline. Try Refresh on Servers or tap Try Fastest."
        s.contains("refused") -> "Why this happens: The server IP rejected connections on that port (relay went offline or restarted). Switch to another relay."
        s.contains("socks5") || s.contains("http") || s.contains("handshake") ->
            "Why this happens: The TCP socket opened, but the proxy refused CONNECT tunneling or auth credentials. Try another verified country or check custom credentials."
        s.contains("tls") || s.contains("certificate") ->
            "Why this happens: End-to-end HTTPS certificate validation through the relay failed, so A VPN blocked it to prevent MITM interception."
        s.contains("leak") ->
            "Why this happens: The proxy exposed your real device IP (transparent proxy), so A VPN rejected it for zero-leak safety."
        s.contains("tun") ->
            "Why this happens: Android OS declined or revoked VPN interface establishment. Tap the button to grant VPN permission again."
        else -> "Why this happens: The selected relay could not complete full end-to-end TLS verification. Tap Try Fastest to connect to a live working server."
    }
}

private fun buildVerboseDiagnosticReport(
    telemetry: TunnelTelemetry,
    logs: List<VpnDiagnosticEntry>
): String = buildString {
    appendLine("=== A VPN Verbose Connection Log ===")
    appendLine("Status: ${telemetry.status.name}")
    appendLine("Foreground Service: ${if (telemetry.isForegroundServiceActive) "Active (specialUse)" else "Inactive"}")
    appendLine("Summary: ${telemetry.handshakeSummary}")
    telemetry.activeNode?.let { n ->
        appendLine("Endpoint: ${n.countryName} (${n.ipAddress}:${n.port}) [${n.relayType.badgeLabel}]")
    }
    telemetry.lastFailureStage?.let { appendLine("Last Failure Stage: $it") }
    telemetry.lastFailureReason?.let { appendLine("Last Failure Detail: $it") }
    appendLine()
    val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    for (entry in logs) {
        val ts = fmt.format(Date(entry.timestampEpochMs))
        appendLine("[$ts] [${entry.severity.name}] ${entry.phase} | ${entry.serverLabel}")
        appendLine("  -> ${entry.summary}: ${entry.detail}")
    }
}
