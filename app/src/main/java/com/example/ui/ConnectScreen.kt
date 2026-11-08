package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.model.VpnNode
import com.example.ui.theme.AmberHandshaking
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SkyBluePrimary
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val FireOrange = Color(0xFFFF5E1A)
private val FireGold = Color(0xFFFFB800)
private val FireCrimson = Color(0xFFFF2A54)

/**
 * Animated GIF-Style Living "A VPN" Logo (`AnimatedGifStyleAVpnLogo`).
 * Continuously renders a 60 FPS cyber-fire & liquid-glass "A" shield with:
 * - Counter-rotating neon plasma & fire rings
 * - Rising glowing flame embers
 * - Custom vector-drawn 3D geometric "A" with an animated holographic shine sweep!
 */
@Composable
fun AnimatedGifStyleAVpnLogo(
    size: Dp = 44.dp,
    cornerRadius: Dp = 13.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gif_logo_anim")

    val ringSpin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_ring_spin"
    )

    val flamePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_flame_pulse"
    )

    val shineSweep by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_shine_sweep"
    )

    val shape = RoundedCornerShape(cornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF081226),
                        Color(0xFF13254A),
                        Color(0xFF091326)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(FireGold, FireOrange, SkyBluePrimary, FireCrimson, FireGold)
                ),
                shape = shape
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val radius = (this.size.minDimension / 2f) * 0.82f

            // 1. Animated Fire & Cyan Radial Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FireOrange.copy(alpha = 0.40f + 0.15f * sin(flamePulse)),
                        SkyBluePrimary.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.15f
                ),
                radius = radius * 1.15f,
                center = center
            )

            // 2. Rotating Cyber-Fire & Plasma Arcs (GIF-style continuous motion)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(FireGold, FireOrange, FireCrimson, SkyBluePrimary, FireGold)
                ),
                startAngle = ringSpin,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )
            drawArc(
                color = SkyBluePrimary,
                startAngle = ringSpin + 180f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            )

            // 3. Rising Flame Embers inside Logo
            for (i in 0 until 5) {
                val cycle = ((flamePulse / (2f * PI.toFloat())) + i * 0.2f) % 1f
                val ex = w * (0.24f + i * 0.13f) + sin(flamePulse + i) * (w * 0.04f)
                val ey = h * (0.85f - cycle * 0.65f)
                drawCircle(
                    color = if (i % 2 == 0) FireGold.copy(alpha = 1f - cycle) else FireOrange.copy(alpha = 1f - cycle),
                    radius = w * 0.038f,
                    center = Offset(ex, ey)
                )
            }

            // 4. Custom Vector-Drawn Glowing "A" Monogram
            val aPath = Path().apply {
                moveTo(w * 0.50f, h * 0.20f) // Apex of "A"
                lineTo(w * 0.76f, h * 0.78f) // Bottom Right leg
                lineTo(w * 0.63f, h * 0.78f)
                lineTo(w * 0.57f, h * 0.63f)
                lineTo(w * 0.43f, h * 0.63f)
                lineTo(w * 0.37f, h * 0.78f)
                lineTo(w * 0.24f, h * 0.78f) // Bottom Left leg
                close()
            }

            // Inner triangle cutout of "A"
            val innerTriangle = Path().apply {
                moveTo(w * 0.50f, h * 0.36f)
                lineTo(w * 0.55f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.52f)
                close()
            }

            // Outer Fire Glow behind "A"
            drawPath(
                path = aPath,
                color = FireOrange.copy(alpha = 0.55f),
                style = Stroke(width = w * 0.07f, join = StrokeJoin.Round)
            )

            // Main Gradient Fill for "A"
            drawPath(
                path = aPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        FireGold,
                        FireOrange
                    )
                )
            )

            // Cutout center of "A"
            drawPath(
                path = innerTriangle,
                color = Color(0xFF0E1D3A)
            )

            // Animated Holographic Laser Shine sweeping across the logo
            val sweepX = w * shineSweep
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.65f),
                        Color.Transparent
                    )
                ),
                start = Offset(sweepX - w * 0.2f, 0f),
                end = Offset(sweepX + w * 0.2f, h),
                strokeWidth = w * 0.12f
            )
        }
    }
}

/**
 * Reusable Ultra-Lightweight iPhone Glassmorphic Card ("iPhone Glass Hi Effect")
 * with smooth Dark/Light mode animated surface interpolation.
 */
@Composable
fun IosGlassCard(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    accentGlowColor: Color? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val topColor by animateColorAsState(
        targetValue = if (isDark) {
            Color(0xFF18263E).copy(alpha = 0.74f)
        } else {
            Color.White.copy(alpha = 0.82f)
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "glass_top_color"
    )
    val bottomColor by animateColorAsState(
        targetValue = if (isDark) {
            Color(0xFF0F192B).copy(alpha = 0.62f)
        } else {
            Color(0xFFF2F7FF).copy(alpha = 0.68f)
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "glass_bottom_color"
    )

    val glassFill = Brush.verticalGradient(colors = listOf(topColor, bottomColor))

    val specularBorder = Brush.linearGradient(
        colors = if (accentGlowColor != null) {
            listOf(
                accentGlowColor.copy(alpha = 0.78f),
                Color.White.copy(alpha = if (isDark) 0.28f else 0.88f),
                accentGlowColor.copy(alpha = 0.38f)
            )
        } else if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.08f),
                SkyBluePrimary.copy(alpha = 0.24f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.96f),
                Color.White.copy(alpha = 0.48f),
                SkyBluePrimary.copy(alpha = 0.22f)
            )
        }
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = glassFill, shape = shape)
            .border(width = 1.2.dp, brush = specularBorder, shape = shape)
    ) {
        content()
    }
}

/**
 * Lightweight iPhone Glassmorphic Connect Screen for **A VPN** with:
 * - Animated Blazing Fire Orb (`FireConnectOrb`) when connecting / connected
 * - Direct "⚡ Fetch Latest & Connect Fastest Tunnel" button
 * - Real-Time Live Telemetry & Speed Indicator (`RealtimeLiveIndicatorPill`)
 * - Prominent Developer Credits Card ("Made with love by Ashir ♥️")
 */
@Composable
fun ConnectScreen(
    uiState: VpnUiState,
    onToggleConnection: () -> Unit,
    onConnectSmartFastest: () -> Unit,
    onOpenNodeSelector: () -> Unit,
    onOpenDeveloperCredits: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val status = uiState.telemetry.status
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING
    val isDark = uiState.preferences.isDarkTheme

    val displayNode = if (!uiState.isAutoFastestMode) {
        uiState.telemetry.activeNode?.takeIf {
            it.countryCode.equals(uiState.selectedNode.countryCode, ignoreCase = true)
        } ?: uiState.selectedNode
    } else {
        uiState.telemetry.activeNode ?: uiState.selectedNode
    }
    val exitCountry = if (!uiState.isAutoFastestMode) {
        uiState.selectedNode.countryName
    } else {
        uiState.telemetry.verifiedExitCountry ?: displayNode.countryName
    }
    val liveExitIp = uiState.telemetry.verifiedExitIp ?: displayNode.ipAddress

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("connect_screen_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Hero iPhone Glass Card with Real-Time Indicator + Blazing Fire Connect Orb
        item {
            IosGlassCard(
                isDark = isDark,
                cornerRadius = 30.dp,
                accentGlowColor = when {
                    isConnected -> FireOrange
                    isConnecting -> FireGold
                    else -> null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_world_network_map_1794084364741),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentScale = ContentScale.Crop,
                        alpha = if (isDark) 0.14f else 0.25f
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Row: Connection Status Badge + Real-Time Live Indicator
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

                        // Central Blazing Fire Animated Connect Orb
                        FireConnectOrb(
                            status = status,
                            onClick = onToggleConnection
                        )

                        Spacer(modifier = Modifier.height(16.dp))

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
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalFireDepartment,
                                                contentDescription = null,
                                                tint = FireOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Connected to ${displayNode.flagEmoji} $exitCountry",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Tunnel IP: $liveExitIp • ⚡ ${displayNode.pingMs} ms • All Apps Active",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = EmeraldConnected
                                        )
                                    }

                                    TunnelConnectionStatus.CONNECTING -> {
                                        Text(
                                            text = "Igniting Ultra-Fast Tunnel…",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = FireOrange
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Connecting to ${displayNode.flagEmoji} ${displayNode.countryName} (${displayNode.pingMs} ms)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    else -> {
                                        Text(
                                            text = if (uiState.isAutoFastestMode) {
                                                "Tap to Ignite A VPN"
                                            } else {
                                                "Connect to ${displayNode.flagEmoji} ${displayNode.countryName}"
                                            },
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (uiState.isAutoFastestMode) {
                                                "Ultra-Fast Auto Tunnel • ${displayNode.flagEmoji} ${displayNode.countryName} (${displayNode.pingMs} ms)"
                                            } else {
                                                "Manual Tunnel Locked • ${displayNode.cityName} (${displayNode.pingMs} ms)"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Direct Live Fetch & Connect Fastest Tunnel Button
                        Button(
                            onClick = onConnectSmartFastest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("smart_fastest_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) {
                                    FireOrange.copy(alpha = 0.16f)
                                } else {
                                    SkyBluePrimary
                                },
                                contentColor = if (isConnected) {
                                    FireOrange
                                } else {
                                    Color.White
                                }
                            )
                        ) {
                            if (uiState.isSyncingApi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isConnected) FireOrange else Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fetching Fastest Live Tunnel…",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isConnected) {
                                        "Fetch Latest & Switch to Fastest Tunnel"
                                    } else {
                                        "Auto-Fetch & Connect Fastest Tunnel"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. iPhone Glass Selected Country Selector Card
        item {
            SelectedCountryGlassCard(
                node = displayNode,
                isAutoMode = uiState.isAutoFastestMode,
                isDark = isDark,
                onOpenNodeList = onOpenNodeSelector
            )
        }

        // 3. Real-Time Download & Upload iPhone Glass Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
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

        // 4. Dedicated Developer Credits Glass Card ("Made with love by Ashir ♥️")
        item {
            DeveloperCreditsGlassBanner(
                isDark = isDark,
                onClick = onOpenDeveloperCredits
            )
        }
    }
}

/**
 * Prominent iPhone Glass Developer Credit Banner honoring Ashir ♥️.
 */
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
            .widthIn(max = 560.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = FireCrimson.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, FireCrimson.copy(alpha = 0.4f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = FireCrimson,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Made with love by Ashir ♥️",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Developer",
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Creator & Lead Architect of A VPN • Tap for Credits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open Developer Credits",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Real-Time Live Indicator Pill showing pulsing status dot, animated wave bars, and live speed/ping.
 */
@Composable
private fun RealtimeLiveIndicatorPill(
    isConnected: Boolean,
    isConnecting: Boolean,
    downloadKbps: Double,
    uploadKbps: Double,
    pingMs: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "realtime_live_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "live_wave_phase"
    )

    val activeColor = when {
        isConnected -> EmeraldConnected
        isConnecting -> FireOrange
        else -> SkyBluePrimary
    }

    val totalSpeedKbps = downloadKbps + uploadKbps
    val liveText = when {
        isConnected -> String.format(Locale.US, "LIVE • %.0f kbps • %d ms", totalSpeedKbps, pingMs)
        isConnecting -> "SYNCING • $pingMs ms"
        else -> "READY • $pingMs ms"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = activeColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.35f)),
        modifier = Modifier.testTag("realtime_live_indicator")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        activeColor.copy(
                            alpha = if (isConnected || isConnecting) dotAlpha else 0.75f
                        )
                    )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(12.dp)
            ) {
                for (i in 0..2) {
                    val dynamicFactor = if (isConnected || isConnecting) {
                        0.45f + 0.55f * ((sin(wavePhase + i * 1.3f) + 1f) / 2f)
                    } else {
                        0.4f + (i * 0.2f)
                    }
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height((12f * dynamicFactor).dp)
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                }
            }

            Text(
                text = liveText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = activeColor,
                maxLines = 1
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when (status) {
                    TunnelConnectionStatus.CONNECTED -> "FIRE • ${exitCountry.take(10).uppercase(Locale.US)}"
                    TunnelConnectionStatus.CONNECTING -> "IGNITING…"
                    TunnelConnectionStatus.DISCONNECTING -> "STOPPING…"
                    TunnelConnectionStatus.ERROR -> "OFFLINE"
                    TunnelConnectionStatus.DISCONNECTED -> "STANDBY"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Central Connect Button with Multi-Layered Blazing Fire Animation when Connecting or Connected!
 */
@Composable
private fun FireConnectOrb(
    status: TunnelConnectionStatus,
    onClick: () -> Unit
) {
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING
    val isFireActive = isConnected || isConnecting

    val infiniteTransition = rememberInfiniteTransition(label = "fire_orb_transition")

    val flamePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 900 else 1400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "flame_phase"
    )

    val emberRise by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 850 else 1300,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ember_rise"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isFireActive) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 650 else 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse_scale"
    )

    val orbScale by animateFloatAsState(
        targetValue = if (isConnected) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "orb_spring"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(196.dp)
            .scale(orbScale)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.36f

            if (isFireActive) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            FireGold.copy(alpha = 0.42f),
                            FireOrange.copy(alpha = 0.28f),
                            FireCrimson.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.52f
                    ),
                    radius = size.minDimension * 0.52f,
                    center = center
                )

                val flamePathOuter = Path()
                val flamePathInner = Path()
                val steps = 72
                for (i in 0..steps) {
                    val angle = (i.toFloat() / steps) * (2f * PI.toFloat())
                    val upwardLift = ((-sin(angle) + 1f) * 0.5f) * 14.dp.toPx()
                    val wave1 = sin(angle * 7f + flamePhase * 2f) * 7.dp.toPx()
                    val wave2 = cos(angle * 11f - flamePhase * 3f) * 5.dp.toPx()
                    val rOuter = baseRadius + 14.dp.toPx() + upwardLift + wave1 + wave2
                    val rInner = baseRadius + 7.dp.toPx() + (upwardLift * 0.55f) + (wave1 * 0.6f)

                    val xOut = center.x + rOuter * cos(angle)
                    val yOut = center.y + rOuter * sin(angle)
                    val xIn = center.x + rInner * cos(angle)
                    val yIn = center.y + rInner * sin(angle)

                    if (i == 0) {
                        flamePathOuter.moveTo(xOut, yOut)
                        flamePathInner.moveTo(xIn, yIn)
                    } else {
                        flamePathOuter.lineTo(xOut, yOut)
                        flamePathInner.lineTo(xIn, yIn)
                    }
                }
                flamePathOuter.close()
                flamePathInner.close()

                drawPath(
                    path = flamePathOuter,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            FireGold.copy(alpha = 0.55f),
                            FireOrange.copy(alpha = 0.45f),
                            FireCrimson.copy(alpha = 0.35f)
                        )
                    )
                )
                drawPath(
                    path = flamePathInner,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            FireGold.copy(alpha = 0.50f),
                            FireOrange.copy(alpha = 0.40f)
                        )
                    )
                )

                for (sparkIdx in 0 until 12) {
                    val seedAngle = (sparkIdx / 12f) * (2f * PI.toFloat()) + (flamePhase * 0.3f)
                    val progress = (emberRise + (sparkIdx * 0.13f)) % 1f
                    val sparkRadiusOffset = baseRadius + (progress * 32.dp.toPx())
                    val sparkX = center.x + cos(seedAngle) * (sparkRadiusOffset * 0.85f)
                    val sparkY = center.y + sin(seedAngle) * (sparkRadiusOffset * 0.85f) - (progress * 26.dp.toPx())
                    val sparkAlpha = (1f - progress).coerceIn(0f, 1f)

                    drawCircle(
                        color = if (sparkIdx % 2 == 0) {
                            FireGold.copy(alpha = sparkAlpha * 0.9f)
                        } else {
                            FireOrange.copy(alpha = sparkAlpha * 0.85f)
                        },
                        radius = (3.8f * (1f - progress * 0.5f)).dp.toPx(),
                        center = Offset(sparkX, sparkY)
                    )
                }
            } else {
                drawCircle(
                    color = SkyBluePrimary.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.45f
                )
                drawCircle(
                    color = SkyBluePrimary.copy(alpha = 0.28f),
                    radius = size.minDimension * 0.39f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        val buttonShape = CircleShape
        val orbBrush = when {
            isConnected -> Brush.linearGradient(
                colors = listOf(FireGold, FireOrange, FireCrimson)
            )
            isConnecting -> Brush.linearGradient(
                colors = listOf(FireOrange, AmberHandshaking, FireCrimson)
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f)
                )
            )
        }

        Surface(
            shape = buttonShape,
            color = Color.Transparent,
            border = BorderStroke(
                width = 3.dp,
                brush = if (isFireActive) {
                    Brush.linearGradient(listOf(Color.White, FireGold, FireOrange))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), SkyBluePrimary))
                }
            ),
            shadowElevation = if (isFireActive) 14.dp else 6.dp,
            modifier = Modifier
                .size(136.dp)
                .clip(buttonShape)
                .background(orbBrush, buttonShape)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = if (isConnected) "Disconnect A VPN" else "Connect A VPN"
                }
                .testTag("connect_toggle_button")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFireActive) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (status) {
                        TunnelConnectionStatus.CONNECTED -> "BLAZING"
                        TunnelConnectionStatus.CONNECTING -> "IGNITING"
                        else -> "CONNECT"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp
                    ),
                    color = if (isFireActive) Color.White else SkyBluePrimary
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
            .widthIn(max = 560.dp)
            .clickable(onClick = onOpenNodeList)
            .testTag("selected_node_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
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
                        "Auto Fastest • ${node.countryName}"
                    } else {
                        "Manual • ${node.countryName}"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${node.cityName} • ⚡ ${node.pingMs} ms (${node.latencyQualityLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = latencyColor(node.pingMs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SkyBluePrimary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tunnels",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = SkyBluePrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Change country",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(12.dp)
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f),
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
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
        pingMs < 55 -> EmeraldConnected
        pingMs < 110 -> SkyBluePrimary
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
