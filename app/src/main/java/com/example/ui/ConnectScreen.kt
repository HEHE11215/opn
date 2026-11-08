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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnNode
import com.example.model.VpnProtocol
import com.example.ui.theme.AmberHandshaking
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SkyBluePrimary
import java.util.Locale

/**
 * Clean, Consumer-Focused One-Tap Connect Screen.
 * Focuses strictly on what users care about: One-tap Auto-Connect, Selected Country,
 * Connection Status, and Live Speed.
 */
@Composable
fun ConnectScreen(
    uiState: VpnUiState,
    onToggleConnection: () -> Unit,
    onConnectSmartFastest: () -> Unit,
    onOpenNodeSelector: () -> Unit,
    onPingSelectedNode: (VpnNode) -> Unit,
    onSelectProtocol: (VpnProtocol) -> Unit,
    onInspectConfig: (VpnNode) -> Unit,
    onVerifyExitIp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val status = uiState.telemetry.status
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val activeNode = uiState.telemetry.activeNode ?: uiState.selectedNode
    val exitCountry = uiState.telemetry.verifiedExitCountry ?: activeNode.countryName

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("connect_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Hero One-Tap Auto-Connect Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isConnected) {
                        EmeraldConnected.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_world_network_map_1794084364741),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop,
                        alpha = if (uiState.preferences.isDarkTheme) 0.16f else 0.40f
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusPillBadge(
                            status = status,
                            exitCountry = exitCountry
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        OneTapConnectOrb(
                            status = status,
                            onClick = onToggleConnection
                        )

                        Spacer(modifier = Modifier.height(20.dp))

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
                                            color = EmeraldConnected
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Connected to ${activeNode.flagEmoji} $exitCountry",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    TunnelConnectionStatus.CONNECTING -> {
                                        Text(
                                            text = "Finding Fastest Server…",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = AmberHandshaking
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Verifying secure connection to ${activeNode.flagEmoji} ${activeNode.countryName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    else -> {
                                        Text(
                                            text = "Tap to Auto-Connect",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (uiState.isSyncingApi) {
                                                "Auto-fetching fastest servers worldwide…"
                                            } else {
                                                "Automatically connects to the fastest server"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Clean Full-Width Smart Auto-Connect Button
                        Button(
                            onClick = onConnectSmartFastest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("smart_fastest_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (isConnected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    Color.White
                                }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isConnected) "Switch to Fastest Server" else "Auto-Connect Best Server",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // 2. Selected Location Selector Card (Clean Consumer Design)
        item {
            SelectedCountryCard(
                node = activeNode,
                isAutoMode = uiState.isAutoFastestMode,
                onOpenNodeList = onOpenNodeSelector
            )
        }

        // 3. Simple Download & Upload Speed Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryStatCard(
                    title = "DOWNLOAD",
                    primaryValue = if (isConnected) String.format(Locale.US, "%.1f kbps", uiState.telemetry.downloadRateKbps) else "0.0 kbps",
                    secondaryValue = formatBytesReadable(uiState.telemetry.bytesDownloaded),
                    icon = Icons.Default.Download,
                    accentColor = SkyBluePrimary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatCard(
                    title = "UPLOAD",
                    primaryValue = if (isConnected) String.format(Locale.US, "%.1f kbps", uiState.telemetry.uploadRateKbps) else "0.0 kbps",
                    secondaryValue = formatBytesReadable(uiState.telemetry.bytesUploaded),
                    icon = Icons.Default.Upload,
                    accentColor = EmeraldConnected,
                    modifier = Modifier.weight(1f)
                )
            }
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
            TunnelConnectionStatus.CONNECTED -> EmeraldConnected.copy(alpha = 0.14f)
            TunnelConnectionStatus.CONNECTING -> AmberHandshaking.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "status_pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when (status) {
            TunnelConnectionStatus.CONNECTED -> EmeraldConnected
            TunnelConnectionStatus.CONNECTING -> AmberHandshaking
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "status_pill_fg"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f)),
        modifier = Modifier.testTag("connection_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
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
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (status) {
                    TunnelConnectionStatus.CONNECTED -> "PROTECTED • ${exitCountry.uppercase(Locale.US)}"
                    TunnelConnectionStatus.CONNECTING -> "CONNECTING…"
                    TunnelConnectionStatus.DISCONNECTING -> "DISCONNECTING…"
                    TunnelConnectionStatus.ERROR -> "DISCONNECTED"
                    TunnelConnectionStatus.DISCONNECTED -> "NOT CONNECTED"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun OneTapConnectOrb(
    status: TunnelConnectionStatus,
    onClick: () -> Unit
) {
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected || isConnecting) 1.14f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 750 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse_scale"
    )

    val orbScale by animateFloatAsState(
        targetValue = if (isConnected) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "orb_spring"
    )

    val primaryRingColor by animateColorAsState(
        targetValue = when (status) {
            TunnelConnectionStatus.CONNECTED -> EmeraldConnected
            TunnelConnectionStatus.CONNECTING -> AmberHandshaking
            else -> SkyBluePrimary
        },
        label = "orb_ring_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(172.dp)
            .scale(orbScale)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
        ) {
            drawCircle(
                color = primaryRingColor.copy(alpha = 0.12f),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = primaryRingColor.copy(alpha = 0.28f),
                radius = (size.minDimension / 2f) * 0.88f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Surface(
            shape = CircleShape,
            color = if (isConnected) EmeraldConnected else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 3.dp,
                color = primaryRingColor
            ),
            shadowElevation = if (isConnected) 10.dp else 4.dp,
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = if (isConnected) "Disconnect VPN" else "Connect VPN"
                }
                .testTag("connect_toggle_button")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isConnected) Color.White else primaryRingColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (status) {
                        TunnelConnectionStatus.CONNECTED -> "STOP"
                        TunnelConnectionStatus.CONNECTING -> "WAIT…"
                        else -> "START"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = if (isConnected) Color.White else primaryRingColor
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clickable(onClick = onOpenNodeList)
            .testTag("selected_node_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = node.flagEmoji,
                        fontSize = 26.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAutoMode) {
                        "Auto Best • ${node.countryName}"
                    } else {
                        node.countryName
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${node.pingMs} ms • ${node.latencyQualityLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = latencyColor(node.pingMs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Change country",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatCard(
    title: String,
    primaryValue: String,
    secondaryValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun latencyColor(pingMs: Int): Color {
    return when {
        pingMs < 95 -> EmeraldConnected
        pingMs < 190 -> SkyBluePrimary
        else -> AmberHandshaking
    }
}

fun formatDurationHhMmSs(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

fun formatBytesReadable(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
