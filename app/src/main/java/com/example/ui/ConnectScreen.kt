package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import com.example.ui.theme.AmberHandshaking
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseAlert
import java.util.Locale

/**
 * Clean Material 3 App Logo container (`AnimatedGifStyleAVpnLogo`).
 * Zero gradients or continuous animations.
 */
@Composable
fun AnimatedGifStyleAVpnLogo(
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.size(size)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_avpn_logo_1794127481273),
            contentDescription = "A VPN Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Clean Material 3 Outlined Card (`M3SectionCard`) replacing the old frosted glass wrapper.
 * Uses solid `MaterialTheme.colorScheme.surface` and `outlineVariant` for instant Day/Night switching.
 */
@Composable
fun M3SectionCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

/**
 * Clean Material 3 Connect Screen for **A VPN** with zero glass UI, zero unnecessary animations,
 * and seamless Day/Night theme adaptation.
 */
@Composable
fun ConnectScreen(
    uiState: VpnUiState,
    onToggleConnection: () -> Unit,
    onConnectSmartFastest: () -> Unit,
    onOpenNodeSelector: () -> Unit,
    onClearDiagnostics: () -> Unit = {},
    onOpenConnectionLogs: () -> Unit = {},
    onOpenDeveloperCredits: () -> Unit = {},
    onToggleWifiOnlyLatency: (Boolean) -> Unit = {},
    onToggleAutoConnectOnStartup: (Boolean) -> Unit = {},
    onRequestAddQuickTile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val status = uiState.telemetry.status
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING
    val isDark = uiState.preferences.isDarkTheme

    val displayNode = if (isConnected || isConnecting) {
        uiState.telemetry.activeNode ?: uiState.selectedNode
    } else if (!uiState.isAutoFastestMode) {
        uiState.selectedNode
    } else {
        uiState.telemetry.activeNode ?: uiState.selectedNode
    }
    val exitCountry = if (isConnected || isConnecting) {
        uiState.telemetry.verifiedExitCountry ?: displayNode.countryName
    } else {
        displayNode.countryName
    }
    val liveExitIp = uiState.telemetry.verifiedExitIp
        ?: displayNode.verifiedExitIp.ifEmpty { displayNode.ipAddress.ifEmpty { "Pending" } }
    val directIp = uiState.telemetry.deviceDirectIp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("connect_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Primary Material 3 Connection Card
        item {
            M3SectionCard(
                cornerRadius = 20.dp,
                borderColor = when {
                    isConnected -> EmeraldConnected.copy(alpha = 0.5f)
                    isConnecting -> AmberHandshaking.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPillBadge(
                            status = status,
                            exitCountry = exitCountry
                        )

                        RealtimeLiveIndicatorPill(
                            isConnected = isConnected,
                            isConnecting = isConnecting,
                            downloadKbps = uiState.telemetry.downloadRateKbps,
                            uploadKbps = uiState.telemetry.uploadRateKbps,
                            pingMs = displayNode.pingMs
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    FireConnectOrb(
                        status = status,
                        isDark = isDark,
                        onClick = onToggleConnection
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (status) {
                            TunnelConnectionStatus.CONNECTED -> {
                                Text(
                                    text = formatDurationHhMmSs(uiState.telemetry.durationSeconds),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = EmeraldConnected
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = EmeraldConnected,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${displayNode.flagEmoji} $exitCountry • TUN0 Active",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                val pingLabel = if (displayNode.pingMs > 0) "${displayNode.pingMs} ms" else "Live…"
                                Spacer(modifier = Modifier.height(2.dp))
                                val ipRouteText = if (!directIp.isNullOrBlank() && directIp != liveExitIp) {
                                    "$directIp → Exit: $liveExitIp • ⚡ $pingLabel"
                                } else {
                                    "Verified Exit IP: $liveExitIp • ⚡ $pingLabel"
                                }
                                Text(
                                    text = ipRouteText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            TunnelConnectionStatus.CONNECTING -> {
                                val pingLabel = if (displayNode.pingMs > 0) "${displayNode.pingMs} ms" else "Verifying…"
                                Text(
                                    text = "Verifying TLS & Starting TUN0…",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AmberHandshaking,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${displayNode.flagEmoji} ${displayNode.countryName} • $pingLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            TunnelConnectionStatus.ERROR -> {
                                Text(
                                    text = "Tunnel Error",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = uiState.telemetry.errorMessage ?: "Select another server or view Connection Log",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            else -> {
                                val pingLabel = if (displayNode.pingMs > 0) "${displayNode.pingMs} ms" else "Scanning…"
                                Text(
                                    text = if (uiState.isAutoFastestMode) {
                                        "Tap Button to Connect"
                                    } else {
                                        "Ready • ${displayNode.flagEmoji} ${displayNode.countryName}"
                                    },
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                val standbySubtitle = if (displayNode.ipAddress.isBlank()) {
                                    "Scanning TLS-verified relays…"
                                } else {
                                    "${displayNode.flagEmoji} ${displayNode.countryName} • ⚡ $pingLabel • ${displayNode.relayType.badgeLabel}"
                                }
                                Text(
                                    text = standbySubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val actionLabel = if (uiState.isSyncingApi) {
                        "Syncing…"
                    } else if (isConnected) {
                        "Fast Switch"
                    } else {
                        "Fast Connect"
                    }

                    if (isConnected) {
                        FilledTonalButton(
                            onClick = onConnectSmartFastest,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .heightIn(min = 48.dp)
                                .testTag("smart_fastest_button")
                        ) {
                            if (uiState.isSyncingApi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Button(
                            onClick = onConnectSmartFastest,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .heightIn(min = 48.dp)
                                .testTag("smart_fastest_button")
                        ) {
                            if (uiState.isSyncingApi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Surface full error diagnostics immediately on hard ERROR
        if (status == TunnelConnectionStatus.ERROR) {
            item {
                VpnDiagnosticPanel(
                    telemetry = uiState.telemetry,
                    isDark = isDark,
                    onClearDiagnostics = onClearDiagnostics,
                    onRetryFastSwitch = onConnectSmartFastest,
                    onBrowseServers = onOpenNodeSelector,
                    onOpenFullLogs = onOpenConnectionLogs
                )
            }
        }

        // 2. Selected Server Card
        item {
            SelectedCountryCard(
                node = displayNode,
                isAutoMode = uiState.isAutoFastestMode,
                onOpenNodeList = onOpenNodeSelector
            )
        }

        // 3. Download & Upload Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryCard(
                    title = "DOWNLOAD",
                    primaryValue = if (isConnected) {
                        String.format(Locale.US, "%.1f kbps", uiState.telemetry.downloadRateKbps)
                    } else {
                        "0.0 kbps"
                    },
                    secondaryValue = formatBytesReadable(uiState.telemetry.bytesDownloaded),
                    icon = Icons.Default.Download,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    title = "UPLOAD",
                    primaryValue = if (isConnected) {
                        String.format(Locale.US, "%.1f kbps", uiState.telemetry.uploadRateKbps)
                    } else {
                        "0.0 kbps"
                    },
                    secondaryValue = formatBytesReadable(uiState.telemetry.bytesUploaded),
                    icon = Icons.Default.Upload,
                    accentColor = EmeraldConnected,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Auto-Connect on Startup Setting
        item {
            val savedServerName = (uiState.preferences.lastSelectedNode ?: displayNode)
                .takeIf { it.ipAddress.isNotBlank() }
                ?.let { "${it.flagEmoji} ${it.countryName}" }
                ?: "Fastest Relay"
            AutoConnectStartupSettingCard(
                enabled = uiState.preferences.autoConnectOnStartup,
                lastServerLabel = savedServerName,
                isDark = isDark,
                onToggle = onToggleAutoConnectOnStartup
            )
        }

        // 5. WorkManager Battery Optimization Setting (Wi-Fi Only Latency Checks)
        item {
            BatteryOptimizationSettingCard(
                wifiOnly = uiState.preferences.wifiOnlyLatencyCheck,
                isDark = isDark,
                onToggle = onToggleWifiOnlyLatency
            )
        }

        // 6. Quick Settings Shade Tile Card
        item {
            QuickSettingsTileSettingCard(
                isDark = isDark,
                onAddTile = onRequestAddQuickTile
            )
        }

        // 7. Connection Log Quick Shortcut Card
        item {
            ConnectionLogShortcutCard(
                logCount = uiState.telemetry.diagnosticLogs.size,
                issueCount = uiState.telemetry.diagnosticLogs.count {
                    it.severity == VpnDiagnosticSeverity.ERROR || it.severity == VpnDiagnosticSeverity.WARNING
                },
                latestSummary = uiState.telemetry.lastFailureStage
                    ?: uiState.telemetry.handshakeSummary.ifBlank { "Tap to inspect handshake logs" },
                onClick = onOpenConnectionLogs
            )
        }

        // 8. Developer Credits Banner ("Made with love by Ashir ♥️")
        item {
            DeveloperCreditsGlassBanner(
                isDark = isDark,
                onClick = onOpenDeveloperCredits
            )
        }
    }
}

@Composable
fun AutoConnectStartupSettingCard(
    enabled: Boolean,
    lastServerLabel: String,
    isDark: Boolean = false,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable { onToggle(!enabled) }
            .testTag("auto_connect_startup_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auto_connect_title),
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
                        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (enabled) "On" else "Off",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else accent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) {
                        stringResource(R.string.auto_connect_subtitle_on, lastServerLabel)
                    } else {
                        stringResource(R.string.auto_connect_subtitle_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("auto_connect_startup_switch")
            )
        }
    }
}

@Composable
fun QuickSettingsTileSettingCard(
    isDark: Boolean = false,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onAddTile)
            .testTag("quick_tile_setting_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.qs_tile_setting_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.qs_tile_setting_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Add Tile",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
fun BatteryOptimizationSettingCard(
    wifiOnly: Boolean,
    isDark: Boolean = false,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable { onToggle(!wifiOnly) }
            .testTag("battery_optimization_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (wifiOnly) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (wifiOnly) Icons.Default.Wifi else Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = if (wifiOnly) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.battery_opt_title),
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
                        color = if (wifiOnly) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (wifiOnly) "Wi-Fi Only" else "All Nets",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (wifiOnly) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        if (wifiOnly) R.string.battery_opt_subtitle_wifi else R.string.battery_opt_subtitle_all
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = wifiOnly,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = EmeraldConnected
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("wifi_only_latency_switch")
            )
        }
    }
}

@Composable
private fun ConnectionLogShortcutCard(
    logCount: Int,
    issueCount: Int,
    latestSummary: String,
    onClick: () -> Unit
) {
    val accent = if (issueCount > 0) AmberHandshaking else MaterialTheme.colorScheme.primary
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onClick)
            .testTag("open_connection_log_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (issueCount > 0) AmberHandshaking.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Connection Log",
                        tint = if (issueCount > 0) AmberHandshaking else MaterialTheme.colorScheme.onPrimaryContainer,
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
                        text = "Connection Log",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (issueCount > 0) "$issueCount alert" else "$logCount logs",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = accent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = latestSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "View Connection Log",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperCreditsGlassBanner(
    isDark: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onClick)
            .testTag("developer_credits_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoseAlert.copy(alpha = 0.14f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = RoseAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Made with love by Ashir ♥️",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Developer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "Creator & Lead Architect • Tap for Info",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open Developer Credits",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun RealtimeLiveIndicatorPill(
    isConnected: Boolean,
    isConnecting: Boolean,
    downloadKbps: Double,
    uploadKbps: Double,
    pingMs: Int
) {
    val activeColor = when {
        isConnected -> EmeraldConnected
        isConnecting -> AmberHandshaking
        else -> MaterialTheme.colorScheme.primary
    }

    val totalSpeedKbps = downloadKbps + uploadKbps
    val pingDisplay = if (pingMs > 0) "$pingMs ms" else "Live…"
    val liveText = when {
        isConnected -> String.format(Locale.US, "LIVE • %.0f kbps • %s", totalSpeedKbps, pingDisplay)
        isConnecting -> "SYNC • $pingDisplay"
        else -> "READY • $pingDisplay"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = activeColor.copy(alpha = 0.12f),
        modifier = Modifier.testTag("realtime_live_indicator")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )

            Text(
                text = liveText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = activeColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun StatusPillBadge(
    status: TunnelConnectionStatus,
    exitCountry: String
) {
    val contentColor = when (status) {
        TunnelConnectionStatus.CONNECTED -> EmeraldConnected
        TunnelConnectionStatus.CONNECTING -> AmberHandshaking
        TunnelConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when (status) {
        TunnelConnectionStatus.CONNECTED -> EmeraldConnected.copy(alpha = 0.14f)
        TunnelConnectionStatus.CONNECTING -> AmberHandshaking.copy(alpha = 0.14f)
        TunnelConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.testTag("connection_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    TunnelConnectionStatus.CONNECTED -> Icons.Default.Lock
                    TunnelConnectionStatus.CONNECTING -> Icons.Default.Sync
                    else -> Icons.Default.LockOpen
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when (status) {
                    TunnelConnectionStatus.CONNECTED -> "ACTIVE • ${exitCountry.take(9).uppercase(Locale.US)}"
                    TunnelConnectionStatus.CONNECTING -> "SYNCING…"
                    TunnelConnectionStatus.DISCONNECTING -> "STOPPING…"
                    TunnelConnectionStatus.ERROR -> "OFFLINE"
                    TunnelConnectionStatus.DISCONNECTED -> "STANDBY"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Simple Material 3 Circular Connect Button (`FireConnectOrb`).
 * Zero glass brushes or press scale animations; mounts a subtle pulse ring
 * ONLY while [status] == [TunnelConnectionStatus.CONNECTING].
 */
@Composable
fun FireConnectOrb(
    status: TunnelConnectionStatus,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING

    val buttonColor = when {
        isConnected -> EmeraldConnected
        isConnecting -> AmberHandshaking
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(152.dp)
    ) {
        // Subtle pulse ring mounted strictly during CONNECTING state (0 idle frames otherwise)
        if (isConnecting) {
            val pulseTransition = rememberInfiniteTransition(label = "connecting_orb_pulse")
            val haloPulseScale by pulseTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "connecting_pulse_scale"
            )
            val haloPulseAlpha by pulseTransition.animateFloat(
                initialValue = 0.50f,
                targetValue = 0.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "connecting_pulse_alpha"
            )

            Box(
                modifier = Modifier
                    .size(144.dp)
                    .scale(haloPulseScale)
                    .clip(CircleShape)
                    .background(AmberHandshaking.copy(alpha = haloPulseAlpha * 0.24f))
                    .border(
                        width = 2.dp,
                        color = AmberHandshaking.copy(alpha = haloPulseAlpha),
                        shape = CircleShape
                    )
                    .testTag("connecting_pulse_ring")
            )
        }

        // Clean Material 3 Tonal Ring Container
        Surface(
            shape = CircleShape,
            color = buttonColor.copy(alpha = 0.12f),
            modifier = Modifier.size(140.dp)
        ) {}

        // Solid Material 3 Primary Circular Action Button
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = buttonColor,
            contentColor = Color.White,
            modifier = Modifier
                .size(116.dp)
                .semantics {
                    contentDescription = if (isConnected) "Disconnect VPN" else "Connect VPN"
                }
                .testTag("primary_connect_orb_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        isConnected -> "ACTIVE"
                        isConnecting -> "SYNC…"
                        else -> "CONNECT"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SelectedCountryCard(
    node: VpnNode,
    isAutoMode: Boolean,
    onOpenNodeList: () -> Unit
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onOpenNodeList)
            .testTag("selected_node_selector_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = node.flagEmoji,
                        fontSize = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = node.countryName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAutoMode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = if (isAutoMode) "Auto" else "Manual",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoMode) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val cardPing = if (node.pingMs > 0) "${node.pingMs} ms" else "Scanning…"
                val endpointSub = if (node.verifiedExitIp.isNotEmpty()) {
                    "Exit ${node.verifiedExitIp}"
                } else if (node.ipAddress.isNotEmpty()) {
                    "${node.cityName} • ${node.relayType.badgeLabel}"
                } else {
                    node.cityName
                }
                Text(
                    text = "$endpointSub • ⚡ $cardPing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = latencyColor(node.pingMs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Choose server",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    primaryValue: String,
    secondaryValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Total: $secondaryValue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

fun latencyColor(pingMs: Int): Color {
    return when {
        pingMs <= 0 -> Color(0xFF0284C7)
        pingMs < 140 -> EmeraldConnected
        pingMs < 260 -> AmberHandshaking
        else -> RoseAlert
    }
}

fun formatDurationHhMmSs(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

fun formatBytesReadable(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
