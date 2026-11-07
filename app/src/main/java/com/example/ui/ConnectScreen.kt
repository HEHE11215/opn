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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
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
 * Primary One-Tap Connect Screen designed with an airy, lightweight daylight aesthetic.
 * Provides instant one-tap connection, smart fastest-node selection, active node switching,
 * protocol selection, and real-time encrypted throughput telemetry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    uiState: VpnUiState,
    onToggleConnection: () -> Unit,
    onConnectSmartFastest: () -> Unit,
    onOpenNodeSelector: () -> Unit,
    onPingSelectedNode: (VpnNode) -> Unit,
    onSelectProtocol: (VpnProtocol) -> Unit,
    onInspectConfig: (VpnNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = uiState.telemetry.status
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING
    val activeNode = uiState.telemetry.activeNode ?: uiState.selectedNode

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("connect_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Hero Card with World Network Topology Banner & One-Tap Power Orb
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isConnected) {
                        EmeraldConnected.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtle topological network map backdrop at the top of the card
                    Image(
                        painter = painterResource(id = R.drawable.img_world_network_map_1794084364741),
                        contentDescription = "Global relay topology map",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Crop,
                        alpha = if (uiState.preferences.isDarkTheme) 0.16f else 0.42f
                    )

                    // Gradient fade into card surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status Pill Badge
                        StatusPillBadge(
                            status = status,
                            virtualIp = uiState.telemetry.virtualIpAddress
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Central One-Tap Encrypted Connect Button with Animated Pulse Rings
                        OneTapConnectOrb(
                            status = status,
                            onClick = onToggleConnection
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Session Timer & Primary Action Caption
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
                                            text = "Tunnel encrypted via ${activeNode.flagEmoji} ${activeNode.cityName} • Tap orb to disconnect",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    TunnelConnectionStatus.CONNECTING -> {
                                        Text(
                                            text = stringResource(R.string.status_connecting),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = AmberHandshaking
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = uiState.telemetry.handshakeSummary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    else -> {
                                        Text(
                                            text = stringResource(R.string.btn_tap_to_connect),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "One-tap zero-configuration tunnel to ${uiState.selectedNode.flagEmoji} ${uiState.selectedNode.cityName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Quick-Action Row: Smart Fastest Node + Inspect Config
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onConnectSmartFastest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("smart_fastest_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isConnected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = if (isConnected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Fastest node",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isConnected) "Switch to Fastest Node" else "Quick Fastest Node",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { onInspectConfig(activeNode) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("inspect_active_config_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Inspect node config",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Audit Node",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Currently Selected Free Node Selector Card
        item {
            SelectedNodeCard(
                node = activeNode,
                isPinging = uiState.singlePingingNodeId == activeNode.id,
                totalAvailableNodes = uiState.allNodes.size,
                onOpenNodeList = onOpenNodeSelector,
                onPingNode = { onPingSelectedNode(activeNode) }
            )
        }

        // 3. Live Encrypted Throughput & Session Telemetry Strip
        item {
            LiveTelemetryGrid(
                isConnected = isConnected,
                downRateKbps = uiState.telemetry.downloadRateKbps,
                upRateKbps = uiState.telemetry.uploadRateKbps,
                totalDownBytes = uiState.telemetry.bytesDownloaded,
                totalUpBytes = uiState.telemetry.bytesUploaded,
                dnsServer = uiState.preferences.dnsServer,
                mtu = uiState.telemetry.activeMtu,
                packetsCount = uiState.telemetry.packetsProcessed
            )
        }

        // 4. Lightweight Protocol & Encryption Selector Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Encryption protocol",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tunnel Encapsulation & Cipher",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "Open-Source Core",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = uiState.preferences.preferredProtocol.cipherDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VpnProtocol.entries.forEach { proto ->
                            val selected = uiState.preferences.preferredProtocol == proto
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectProtocol(proto) },
                                label = {
                                    Text(
                                        text = proto.shortName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected protocol",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("protocol_chip_${proto.name.lowercase(Locale.US)}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPillBadge(
    status: TunnelConnectionStatus,
    virtualIp: String
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    TunnelConnectionStatus.CONNECTED -> Icons.Default.Lock
                    TunnelConnectionStatus.CONNECTING -> Icons.Default.Sync
                    else -> Icons.Default.LockOpen
                },
                contentDescription = "Tunnel lock state",
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (status) {
                    TunnelConnectionStatus.CONNECTED -> "ENCRYPTED TUNNEL • VIP $virtualIp"
                    TunnelConnectionStatus.CONNECTING -> "HANDSHAKING TLS & TUN0…"
                    TunnelConnectionStatus.DISCONNECTING -> "CLOSING TUNNEL…"
                    TunnelConnectionStatus.ERROR -> "CONNECTION INTERRUPTED"
                    TunnelConnectionStatus.DISCONNECTED -> "UNPROTECTED • READY TO CONNECT"
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
            .size(176.dp)
            .scale(orbScale)
    ) {
        // Outer subtle breathing halo ring
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

        // Main Tappable Power / Shield Button
        Surface(
            shape = CircleShape,
            color = if (isConnected) EmeraldConnected else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 3.dp,
                color = primaryRingColor
            ),
            shadowElevation = if (isConnected) 10.dp else 4.dp,
            modifier = Modifier
                .size(134.dp)
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
                        TunnelConnectionStatus.CONNECTED -> "ACTIVE"
                        TunnelConnectionStatus.CONNECTING -> "WAIT…"
                        else -> "CONNECT"
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
private fun SelectedNodeCard(
    node: VpnNode,
    isPinging: Boolean,
    totalAvailableNodes: Int,
    onOpenNodeList: () -> Unit,
    onPingNode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clickable(onClick = onOpenNodeList)
            .testTag("selected_node_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Selected worldwide relay",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SELECTED FREE WORLDWIDE NODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "$totalAvailableNodes Free Nodes →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Country Flag Badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(52.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${node.cityName}, ${node.countryName}",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${node.ipAddress}:${node.port} • ${node.operatorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Live Ping Pill + Quick Ping Button
                IconButton(
                    onClick = onPingNode,
                    modifier = Modifier.testTag("ping_selected_node_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Measure latency to selected node",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Change server location",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Node Metrics Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onPingNode,
                    label = {
                        Text(
                            text = if (isPinging) "Pinging…" else "${node.pingMs} ms (${node.latencyQualityLabel})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Ping latency",
                            modifier = Modifier.size(15.dp),
                            tint = latencyColor(node.pingMs)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    border = null
                )

                AssistChip(
                    onClick = onOpenNodeList,
                    label = {
                        Text(
                            text = "${node.downloadSpeedMbps} Mbps",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                )

                if (node.isLiveFromPublicApi) {
                    AssistChip(
                        onClick = onOpenNodeList,
                        label = {
                            Text(
                                text = "Live API",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldConnected
                            )
                        },
                        border = null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = EmeraldConnected.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryGrid(
    isConnected: Boolean,
    downRateKbps: Double,
    upRateKbps: Double,
    totalDownBytes: Long,
    totalUpBytes: Long,
    dnsServer: String,
    mtu: Int,
    packetsCount: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryStatCard(
                title = "DOWNLOAD",
                primaryValue = if (isConnected) String.format(Locale.US, "%.1f kbps", downRateKbps) else "0.0 kbps",
                secondaryValue = "Total: ${formatBytesReadable(totalDownBytes)}",
                icon = Icons.Default.Download,
                accentColor = SkyBluePrimary,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "UPLOAD",
                primaryValue = if (isConnected) String.format(Locale.US, "%.1f kbps", upRateKbps) else "0.0 kbps",
                secondaryValue = "Total: ${formatBytesReadable(totalUpBytes)}",
                icon = Icons.Default.Upload,
                accentColor = EmeraldConnected,
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "DNS Resolver",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DNS $dnsServer • MTU $mtu",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (isConnected) "$packetsCount pkts framed" else "Zero-Log Tunnel Ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        Column(modifier = Modifier.padding(14.dp)) {
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
        pingMs < 70 -> EmeraldConnected
        pingMs < 150 -> SkyBluePrimary
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
