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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * Uses the exact same 3D Cyber-Fire & Cyan "A" Shield emblem as the App Launcher Icon
 * (`R.drawable.img_avpn_logo_1794127481273`), enhanced with 60 FPS GIF-style motion:
 * - Counter-rotating neon plasma & cyber-fire orbital rings
 * - Breathing 3D shield pulse
 * - Rising glowing flame embers
 * - Diagonal holographic glass laser shine sweep
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
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_ring_spin"
    )

    val flamePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_flame_pulse"
    )

    val shieldBreath by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_shield_breath"
    )

    val shineSweep by infiniteTransition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1750, easing = FastOutSlowInEasing),
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
            .background(Color(0xFF0A1324))
            .border(
                width = 1.4.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(FireGold, FireOrange, SkyBluePrimary, FireCrimson, FireGold)
                ),
                shape = shape
            )
    ) {
        // 1. Exact App Launcher "A" Shield Emblem with subtle breathing animation
        Image(
            painter = painterResource(id = R.drawable.img_avpn_logo_1794127481273),
            contentDescription = "A VPN Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(shieldBreath)
        )

        // 2. 60 FPS GIF-Style Animated Overlay: Rotating Fire/Cyan Rings + Embers + Glass Laser Shine
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val radius = (this.size.minDimension / 2f) * 0.88f

            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(FireGold, FireOrange, Color.Transparent, SkyBluePrimary, FireGold)
                ),
                startAngle = ringSpin,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
            )
            drawArc(
                color = SkyBluePrimary.copy(alpha = 0.85f),
                startAngle = ringSpin + 180f,
                sweepAngle = 85f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = w * 0.04f, cap = StrokeCap.Round)
            )

            // Rising glowing fire sparks
            for (i in 0 until 4) {
                val cycle = ((flamePulse / (2f * PI.toFloat())) + i * 0.25f) % 1f
                val ex = w * (0.22f + i * 0.18f) + sin(flamePulse + i) * (w * 0.03f)
                val ey = h * (0.86f - cycle * 0.68f)
                drawCircle(
                    color = if (i % 2 == 0) {
                        FireGold.copy(alpha = (1f - cycle) * 0.85f)
                    } else {
                        SkyBluePrimary.copy(alpha = (1f - cycle) * 0.85f)
                    },
                    radius = w * 0.03f,
                    center = Offset(ex, ey)
                )
            }

            // Diagonal Holographic Glass Shine Sweep
            val sweepX = w * shineSweep
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                ),
                start = Offset(sweepX - w * 0.22f, 0f),
                end = Offset(sweepX + w * 0.22f, h),
                strokeWidth = w * 0.14f
            )
        }
    }
}

/**
 * Reusable Ultra-Lightweight iOS Frosted Liquid Glass Card ("iPhone Glass Hi Effect")
 * with diagonal specular glass reflection, inner frosted rim, and smooth Dark/Light mode transition.
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
    val topColor by animateColorAsState(
        targetValue = if (isDark) {
            Color(0xFF17253F).copy(alpha = 0.68f)
        } else {
            Color.White.copy(alpha = 0.78f)
        },
        animationSpec = tween(480, easing = FastOutSlowInEasing),
        label = "glass_top_color"
    )
    val bottomColor by animateColorAsState(
        targetValue = if (isDark) {
            Color(0xFF0C1526).copy(alpha = 0.54f)
        } else {
            Color(0xFFEEF5FF).copy(alpha = 0.62f)
        },
        animationSpec = tween(480, easing = FastOutSlowInEasing),
        label = "glass_bottom_color"
    )

    val glassFill = Brush.verticalGradient(colors = listOf(topColor, bottomColor))

    val specularBorder = Brush.linearGradient(
        colors = if (accentGlowColor != null) {
            listOf(
                accentGlowColor.copy(alpha = 0.75f),
                Color.White.copy(alpha = if (isDark) 0.32f else 0.92f),
                accentGlowColor.copy(alpha = 0.35f)
            )
        } else if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.08f),
                SkyBluePrimary.copy(alpha = 0.22f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.98f),
                Color.White.copy(alpha = 0.52f),
                SkyBluePrimary.copy(alpha = 0.24f)
            )
        }
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = glassFill, shape = shape)
            .border(width = 1.2.dp, brush = specularBorder, shape = shape)
    ) {
        // Subtle diagonal iOS Frosted Glass Specular Sheen
        Canvas(modifier = Modifier.matchParentSize()) {
            val sheenPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * 0.65f, 0f)
                lineTo(size.width * 0.25f, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = sheenPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.06f else 0.26f),
                        Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.6f, size.height * 0.6f)
                )
            )
        }
        content()
    }
}

/**
 * Compact Animated Glass Pill Button with spring press feedback, generous inner padding,
 * outer margin support, and short crisp single-line typography.
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
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_press_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "btn_shimmer")
    val borderGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btn_border_glow"
    )

    val bgBrush = if (isPrimaryFilled) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor,
                accentColor.copy(alpha = 0.84f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    accentColor.copy(alpha = 0.20f),
                    Color(0xFF13223A).copy(alpha = 0.72f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.88f),
                    accentColor.copy(alpha = 0.14f)
                )
            }
        )
    }

    val fgColor = if (isPrimaryFilled) {
        Color.White
    } else if (isDark) {
        Color.White
    } else {
        accentColor
    }

    val shape = RoundedCornerShape(16.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(pressScale)
            .height(44.dp)
            .clip(shape)
            .background(brush = bgBrush, shape = shape)
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = borderGlowAlpha),
                        Color.White.copy(alpha = if (isDark) 0.30f else 0.85f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = fgColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Lightweight iPhone Glassmorphic Connect Screen for **A VPN** with:
 * - Animated Liquid-Glass & Blazing Fire Connect Button (`FireConnectOrb`)
 * - Compact, short-text Glass Action Buttons (`"⚡ Fast Connect"` & `"📌 Status Bar"`) with proper margins/padding
 * - Real-Time Live Telemetry & Speed Indicator (`RealtimeLiveIndicatorPill`)
 * - Prominent Developer Credits Card ("Made with love by Ashir ♥️")
 */
@Composable
fun ConnectScreen(
    uiState: VpnUiState,
    onToggleConnection: () -> Unit,
    onConnectSmartFastest: () -> Unit,
    onOpenNodeSelector: () -> Unit,
    onAddTopStatusBarShortcut: () -> Unit = {},
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Hero iPhone Frosted Glass Card
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
                    .widthIn(max = 560.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_world_network_map_1794084364741),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(195.dp),
                        contentScale = ContentScale.Crop,
                        alpha = if (isDark) 0.12f else 0.20f
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Central Animated Liquid-Glass & Cyber-Fire Connect Button
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
                                                text = "${displayNode.flagEmoji} $exitCountry • Wi-Fi Warp",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "IP: $liveExitIp • ⚡ ${displayNode.pingMs} ms • Full Wi-Fi Speed",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            ),
                                            color = EmeraldConnected
                                        )
                                    }

                                    TunnelConnectionStatus.CONNECTING -> {
                                        Text(
                                            text = "Starting Wi-Fi Warp…",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = FireOrange
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${displayNode.flagEmoji} ${displayNode.countryName} • ${displayNode.pingMs} ms",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    else -> {
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
                                        Text(
                                            text = "${displayNode.flagEmoji} ${displayNode.countryName} • ⚡ ${displayNode.pingMs} ms • Wi-Fi Warp",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Compact 2-Button Glass Row with short labels & balanced margins/padding
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    .weight(1f)
                                    .testTag("smart_fastest_button")
                            )

                            AnimatedGlassActionPillButton(
                                text = "Status Bar",
                                icon = Icons.Default.NotificationsActive,
                                isDark = isDark,
                                accentColor = EmeraldConnected,
                                isPrimaryFilled = false,
                                onClick = onAddTopStatusBarShortcut,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_status_bar_shortcut_button")
                            )
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
                            color = MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        isConnecting -> "SYNC • $pingMs ms"
        else -> "READY • $pingMs ms"
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
                    .background(
                        activeColor.copy(
                            alpha = if (isConnected || isConnecting) dotAlpha else 0.75f
                        )
                    )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(11.dp)
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
                            .height((11f * dynamicFactor).dp)
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
                maxLines = 1
            )
        }
    }
}

/**
 * Central Animated Liquid-Glass & Cyber-Fire Connect Button (`FireConnectOrb`).
 * Features continuous 60 FPS orbital arcs, liquid wave rings, tactile spring press animation,
 * and crisp compact button text.
 */
@Composable
private fun FireConnectOrb(
    status: TunnelConnectionStatus,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val isConnected = status == TunnelConnectionStatus.CONNECTED
    val isConnecting = status == TunnelConnectionStatus.CONNECTING
    val isFireActive = isConnected || isConnecting

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "fire_orb_transition")

    val flamePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 850 else 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "flame_phase"
    )

    val ringSpin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 1200 else 2800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_ring_spin"
    )

    val emberRise by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 800 else 1350,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ember_rise"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isFireActive) 1.09f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 600 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse_scale"
    )

    val pressScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            isConnected -> 1.03f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orb_spring"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(182.dp)
            .scale(pressScale)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.35f

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
                        radius = size.minDimension * 0.50f
                    ),
                    radius = size.minDimension * 0.50f,
                    center = center
                )

                val flamePathOuter = Path()
                val flamePathInner = Path()
                val steps = 64
                for (i in 0..steps) {
                    val angle = (i.toFloat() / steps) * (2f * PI.toFloat())
                    val upwardLift = ((-sin(angle) + 1f) * 0.5f) * 12.dp.toPx()
                    val wave1 = sin(angle * 7f + flamePhase * 2f) * 6.dp.toPx()
                    val wave2 = cos(angle * 11f - flamePhase * 3f) * 4.dp.toPx()
                    val rOuter = baseRadius + 12.dp.toPx() + upwardLift + wave1 + wave2
                    val rInner = baseRadius + 6.dp.toPx() + (upwardLift * 0.55f) + (wave1 * 0.6f)

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

                for (sparkIdx in 0 until 10) {
                    val seedAngle = (sparkIdx / 10f) * (2f * PI.toFloat()) + (flamePhase * 0.3f)
                    val progress = (emberRise + (sparkIdx * 0.13f)) % 1f
                    val sparkRadiusOffset = baseRadius + (progress * 28.dp.toPx())
                    val sparkX = center.x + cos(seedAngle) * (sparkRadiusOffset * 0.85f)
                    val sparkY = center.y + sin(seedAngle) * (sparkRadiusOffset * 0.85f) - (progress * 22.dp.toPx())
                    val sparkAlpha = (1f - progress).coerceIn(0f, 1f)

                    drawCircle(
                        color = if (sparkIdx % 2 == 0) {
                            FireGold.copy(alpha = sparkAlpha * 0.85f)
                        } else {
                            FireOrange.copy(alpha = sparkAlpha * 0.75f)
                        },
                        radius = (3.2f * (1f - progress * 0.5f)).dp.toPx(),
                        center = Offset(sparkX, sparkY)
                    )
                }
            } else {
                // Animated Liquid-Glass Orbital Rings even in Standby mode!
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SkyBluePrimary.copy(alpha = 0.28f),
                            SkyBluePrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.48f
                    ),
                    radius = size.minDimension * 0.48f,
                    center = center
                )

                val orbitR = baseRadius + 12.dp.toPx()
                val arcTopLeft = Offset(center.x - orbitR, center.y - orbitR)
                val arcSize = Size(orbitR * 2f, orbitR * 2f)
                for (seg in 0 until 3) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                SkyBluePrimary.copy(alpha = 0.75f),
                                EmeraldConnected.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        ),
                        startAngle = ringSpin + seg * 120f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Core Interactive Frosted Glass Orb Button
        val orbGradient = when {
            isConnected -> Brush.linearGradient(
                colors = listOf(
                    FireGold,
                    FireOrange,
                    FireCrimson
                )
            )
            isConnecting -> Brush.linearGradient(
                colors = listOf(
                    FireOrange,
                    AmberHandshaking,
                    FireGold
                )
            )
            isDark -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFF152744),
                    Color(0xFF0E1B31),
                    SkyBluePrimary.copy(alpha = 0.85f)
                )
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    SkyBluePrimary,
                    Color(0xFF0284C7),
                    Color(0xFF2563EB)
                )
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(brush = orbGradient, shape = CircleShape)
                .border(
                    width = 2.8.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.90f),
                            if (isFireActive) FireGold else SkyBluePrimary.copy(alpha = 0.5f)
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
            // Specular glass highlight inside the orb
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.55f
                    ),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width / 2f, size.height * 0.38f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
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
                Spacer(modifier = Modifier.height(5.dp))
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
                        letterSpacing = 1.1.sp
                    ),
                    maxLines = 1
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = node.countryName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${node.cityName} • ⚡ ${node.pingMs} ms • Wi-Fi Warp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = latencyColor(node.pingMs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SkyBluePrimary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = SkyBluePrimary,
                            fontSize = 11.sp
                        )
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
        pingMs < 85 -> EmeraldConnected
        pingMs < 180 -> AmberHandshaking
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
