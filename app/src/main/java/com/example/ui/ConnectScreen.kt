package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.SkyBluePrimary
import java.util.Locale

private val FireOrange = Color(0xFFFF5E1A)
private val FireGold = Color(0xFFFFB800)
private val FireCrimson = Color(0xFFFF2A54)

/**
 * Ultra-Lightweight "A VPN" Logo (`AnimatedGifStyleAVpnLogo`).
 * Zero continuous infinite frame loops so TopAppBar & overlays draw at 0% idle CPU/GPU.
 */
@Composable
fun AnimatedGifStyleAVpnLogo(
    size: Dp = 44.dp,
    cornerRadius: Dp = 13.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF0A1324))
            .border(
                width = 1.4.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(FireGold, FireOrange, SkyBluePrimary, FireCrimson, FireGold)
                ),
                shape = shape
            )
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
 * Ultra-Lightweight iOS Frosted Glass Card (`IosGlassCard`) using pure Compose gradient fill +
 * crisp border (zero per-frame Canvas `Path()` allocations during scroll).
 */
@Composable
fun IosGlassCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    accentGlowColor: Color? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val glassFill = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF17253F).copy(alpha = 0.72f),
                    Color(0xFF0C1526).copy(alpha = 0.58f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.84f),
                    Color(0xFFEEF5FF).copy(alpha = 0.68f)
                )
            )
        }
    }

    val specularBorder = remember(isDark, accentGlowColor) {
        Brush.linearGradient(
            colors = if (accentGlowColor != null) {
                listOf(
                    accentGlowColor.copy(alpha = 0.70f),
                    Color.White.copy(alpha = if (isDark) 0.28f else 0.90f),
                    accentGlowColor.copy(alpha = 0.35f)
                )
            } else if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.08f),
                    SkyBluePrimary.copy(alpha = 0.20f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.96f),
                    Color.White.copy(alpha = 0.55f),
                    SkyBluePrimary.copy(alpha = 0.22f)
                )
            }
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = glassFill, shape = shape)
            .border(width = 1.1.dp, brush = specularBorder, shape = shape)
    ) {
        content()
    }
}

/**
 * Lightweight Pill Button with spring press feedback, generous inner padding,
 * single-line crisp typography (`softWrap = false`), and 48dp accessible height.
 */
@Composable
fun AnimatedGlassActionPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    accentColor: Color,
    isPrimaryFilled: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_press_scale"
    )

    val bgBrush = if (isPrimaryFilled) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.86f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    accentColor.copy(alpha = 0.20f),
                    Color(0xFF13223A).copy(alpha = 0.74f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.90f),
                    accentColor.copy(alpha = 0.14f)
                )
            }
        )
    }

    val fgColor = if (isPrimaryFilled || isDark) Color.White else accentColor
    val shape = RoundedCornerShape(16.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(pressScale)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(brush = bgBrush, shape = shape)
            .border(
                width = 1.2.dp,
                color = accentColor.copy(alpha = 0.58f),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = fgColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = fgColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Ultra-Light Frosted Glass Connect Screen for **A VPN** with zero idle frame ticks,
 * spacious button padding, and direct troubleshooting jump to Connection Log.
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Hero Frosted Glass Card
        item {
            IosGlassCard(
                isDark = isDark,
                cornerRadius = 28.dp,
                accentGlowColor = when {
                    isConnected -> FireOrange
                    isConnecting -> FireGold
                    else -> SkyBluePrimary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_world_network_map_1794084364741),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Crop,
                        alpha = if (isDark) 0.10f else 0.18f
                    )

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

                        Spacer(modifier = Modifier.height(16.dp))

                        FireConnectOrb(
                            status = status,
                            isDark = isDark,
                            onClick = onToggleConnection
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        AnimatedContent(
                            targetState = status,
                            label = "status_caption_anim"
                        ) { targetStatus ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                when (targetStatus) {
                                    TunnelConnectionStatus.CONNECTED -> {
                                        Text(
                                            text = formatDurationHhMmSs(uiState.telemetry.durationSeconds),
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isDark) FireGold else FireOrange
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalFireDepartment,
                                                contentDescription = null,
                                                tint = FireOrange,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
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
                                            color = EmeraldConnected,
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
                                            color = FireOrange,
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
                                            color = RoseAlert,
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
                                                "Tap Orb to Connect"
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
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        AnimatedGlassActionPillButton(
                            text = if (uiState.isSyncingApi) {
                                "Syncing…"
                            } else if (isConnected) {
                                "Fast Switch"
                            } else {
                                "Fast Connect"
                            },
                            icon = Icons.Default.Bolt,
                            isDark = isDark,
                            accentColor = if (isConnected) FireOrange else SkyBluePrimary,
                            isPrimaryFilled = !isConnected,
                            isLoading = uiState.isSyncingApi,
                            onClick = onConnectSmartFastest,
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .testTag("smart_fastest_button")
                        )
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
            SelectedCountryGlassCard(
                node = displayNode,
                isAutoMode = uiState.isAutoFastestMode,
                isDark = isDark,
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
                TelemetryGlassCard(
                    title = "DOWNLOAD",
                    primaryValue = if (isConnected) {
                        String.format(Locale.US, "%.1f kbps", uiState.telemetry.downloadRateKbps)
                    } else {
                        "0.0 kbps"
                    },
                    secondaryValue = formatBytesReadable(uiState.telemetry.bytesDownloaded),
                    icon = Icons.Default.Download,
                    accentColor = SkyBluePrimary,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                TelemetryGlassCard(
                    title = "UPLOAD",
                    primaryValue = if (isConnected) {
                        String.format(Locale.US, "%.1f kbps", uiState.telemetry.uploadRateKbps)
                    } else {
                        "0.0 kbps"
                    },
                    secondaryValue = formatBytesReadable(uiState.telemetry.bytesUploaded),
                    icon = Icons.Default.Upload,
                    accentColor = FireOrange,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Settings & Automation Section
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

        // 7. Lightweight Connection Log Quick Shortcut Card
        item {
            ConnectionLogShortcutCard(
                logCount = uiState.telemetry.diagnosticLogs.size,
                issueCount = uiState.telemetry.diagnosticLogs.count {
                    it.severity == VpnDiagnosticSeverity.ERROR || it.severity == VpnDiagnosticSeverity.WARNING
                },
                latestSummary = uiState.telemetry.lastFailureStage
                    ?: uiState.telemetry.handshakeSummary.ifBlank { "Tap to inspect handshake logs" },
                isDark = isDark,
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
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (enabled) FireOrange else SkyBluePrimary
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
        accentGlowColor = if (enabled) FireOrange else null,
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
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.40f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp)
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
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (enabled) "On" else "Off",
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
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FireOrange,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("auto_connect_startup_switch")
            )
        }
    }
}

@Composable
fun QuickSettingsTileSettingCard(
    isDark: Boolean,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier
) {
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
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
                color = SkyBluePrimary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.40f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(19.dp)
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
                color = SkyBluePrimary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.35f)),
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
                            color = SkyBluePrimary
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
    isDark: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (wifiOnly) EmeraldConnected else SkyBluePrimary
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
        accentGlowColor = if (wifiOnly) EmeraldConnected else null,
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
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.40f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (wifiOnly) Icons.Default.Wifi else Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp)
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
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (wifiOnly) "Wi-Fi Only" else "All Nets",
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
                    checkedThumbColor = Color.White,
                    checkedTrackColor = EmeraldConnected,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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
    isDark: Boolean,
    onClick: () -> Unit
) {
    val accent = if (issueCount > 0) AmberHandshaking else SkyBluePrimary
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
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
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.40f)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Connection Log",
                        tint = accent,
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
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f)
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
                color = SkyBluePrimary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SkyBluePrimary
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "View Connection Log",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperCreditsGlassBanner(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 22.dp,
        accentGlowColor = FireOrange,
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
                    color = FireCrimson.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, FireCrimson.copy(alpha = 0.4f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = FireCrimson,
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
                            tint = SkyBluePrimary,
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
        isConnecting -> FireOrange
        else -> SkyBluePrimary
    }

    val totalSpeedKbps = downloadKbps + uploadKbps
    val pingDisplay = if (pingMs > 0) "$pingMs ms" else "Live…"
    val liveText = when {
        isConnected -> String.format(Locale.US, "LIVE • %.0f kbps • %s", totalSpeedKbps, pingDisplay)
        isConnecting -> "SYNC • $pingDisplay"
        else -> "READY • $pingDisplay"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = activeColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.35f)),
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
    val containerColor by animateColorAsState(
        targetValue = when (status) {
            TunnelConnectionStatus.CONNECTED -> FireOrange.copy(alpha = 0.16f)
            TunnelConnectionStatus.CONNECTING -> AmberHandshaking.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        label = "status_pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when (status) {
            TunnelConnectionStatus.CONNECTED -> FireOrange
            TunnelConnectionStatus.CONNECTING -> AmberHandshaking
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "status_pill_fg"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
        modifier = Modifier.testTag("connection_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    TunnelConnectionStatus.CONNECTED -> Icons.Default.LocalFireDepartment
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
 * Ultra-Light Central Frosted Glass Connect Orb (`FireConnectOrb`).
 * Zero continuous idle loops when disconnected/connected; mounts a subtle pulse transition
 * ONLY while [status] == [TunnelConnectionStatus.CONNECTING].
 */
@Composable
fun FireConnectOrb(
    status: TunnelConnectionStatus,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING
    val isFireActive = isConnected || isConnecting

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isConnected -> 1.03f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orb_spring"
    )

    val haloColor = when {
        isConnected -> FireOrange
        isConnecting -> AmberHandshaking
        else -> SkyBluePrimary
    }

    val orbGradient = when {
        isConnected -> Brush.linearGradient(listOf(FireGold, FireOrange, FireCrimson))
        isConnecting -> Brush.linearGradient(listOf(FireOrange, AmberHandshaking, FireGold))
        isDark -> Brush.linearGradient(listOf(Color(0xFF152744), Color(0xFF0E1B31), SkyBluePrimary.copy(alpha = 0.85f)))
        else -> Brush.linearGradient(listOf(SkyBluePrimary, Color(0xFF0284C7), Color(0xFF2563EB)))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(164.dp)
            .scale(pressScale)
    ) {
        var buttonPulseScale = 1f

        // Subtle pulse transition mounted strictly during CONNECTING state (0 idle frames otherwise)
        if (isConnecting) {
            val pulseTransition = rememberInfiniteTransition(label = "connecting_orb_pulse")
            val haloPulseScale by pulseTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "connecting_pulse_scale"
            )
            val haloPulseAlpha by pulseTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "connecting_pulse_alpha"
            )
            val orbBreath by pulseTransition.animateFloat(
                initialValue = 0.97f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "connecting_orb_breath"
            )
            buttonPulseScale = orbBreath

            Box(
                modifier = Modifier
                    .size(156.dp)
                    .scale(haloPulseScale)
                    .clip(CircleShape)
                    .background(AmberHandshaking.copy(alpha = haloPulseAlpha * 0.28f))
                    .border(
                        width = 2.dp,
                        color = AmberHandshaking.copy(alpha = haloPulseAlpha),
                        shape = CircleShape
                    )
                    .testTag("connecting_pulse_ring")
            )
        }

        // Lightweight outer static ring halo
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(CircleShape)
                .background(haloColor.copy(alpha = if (isFireActive) 0.14f else 0.08f))
                .border(
                    width = 1.5.dp,
                    color = haloColor.copy(alpha = if (isFireActive) 0.45f else 0.28f),
                    shape = CircleShape
                )
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(128.dp)
                .scale(buttonPulseScale)
                .clip(CircleShape)
                .background(brush = orbGradient, shape = CircleShape)
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.90f),
                            if (isFireActive) FireGold else SkyBluePrimary.copy(alpha = 0.55f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .semantics {
                    contentDescription = if (isConnected) "Disconnect VPN" else "Connect VPN"
                }
                .testTag("primary_connect_orb_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(14.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isConnected) {
                            Icons.Default.LocalFireDepartment
                        } else {
                            Icons.Default.PowerSettingsNew
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
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
                        letterSpacing = 0.8.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SelectedCountryGlassCard(
    node: VpnNode,
    isAutoMode: Boolean,
    isDark: Boolean,
    onOpenNodeList: () -> Unit
) {
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 22.dp,
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
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAutoMode) {
                            SkyBluePrimary.copy(alpha = 0.16f)
                        } else {
                            FireOrange.copy(alpha = 0.18f)
                        }
                    ) {
                        Text(
                            text = if (isAutoMode) "Auto" else "Manual",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoMode) SkyBluePrimary else FireOrange
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
                shape = RoundedCornerShape(12.dp),
                color = SkyBluePrimary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.35f)),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = SkyBluePrimary,
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
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryGlassCard(
    title: String,
    primaryValue: String,
    secondaryValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
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
        pingMs <= 0 -> SkyBluePrimary
        pingMs < 140 -> EmeraldConnected
        pingMs < 260 -> AmberHandshaking
        else -> Color(0xFFE11D48)
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
