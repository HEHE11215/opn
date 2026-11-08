package com.example

import android.Manifest
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnNode
import com.example.ui.AetherTab
import com.example.ui.AnimatedGifStyleAVpnLogo
import com.example.ui.ConnectScreen
import com.example.ui.IosGlassCard
import com.example.ui.NodeListScreen
import com.example.ui.VpnEventPopup
import com.example.ui.VpnPopupTone
import com.example.ui.VpnUiState
import com.example.ui.VpnViewModel
import com.example.ui.theme.AetherNodeTheme
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.SkyBluePrimary
import com.example.vpn.AetherVpnStateBus
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val FireOrange = Color(0xFFFF5E1A)
private val FireGold = Color(0xFFFFB800)
private val FireCrimson = Color(0xFFFF2A54)

/**
 * MainActivity for **A VPN** — Crafted with love by **Ashir ♥️**.
 */
class MainActivity : ComponentActivity() {

    private val vpnViewModel: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by vpnViewModel.uiState.collectAsStateWithLifecycle()

            AetherNodeTheme(darkTheme = uiState.preferences.isDarkTheme) {
                AVpnRootApp(
                    uiState = uiState,
                    viewModel = vpnViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVpnRootApp(
    uiState: VpnUiState,
    viewModel: VpnViewModel
) {
    val context = LocalContext.current
    var showStartupSplash by rememberSaveable { mutableStateOf(true) }
    var bootProgress by remember { mutableFloatStateOf(0.08f) }
    var pendingNodeToConnect by remember { mutableStateOf<VpnNode?>(null) }
    var pendingIsManualSelection by remember { mutableStateOf(false) }

    // Animated GIF-style boot sequence progression
    LaunchedEffect(showStartupSplash) {
        if (showStartupSplash) {
            val steps = 20
            for (i in 1..steps) {
                bootProgress = i / steps.toFloat()
                delay(85L)
            }
            delay(220L)
            showStartupSplash = false
        }
    }

    // Back navigation handler for secondary tab
    BackHandler(enabled = uiState.activeTab != AetherTab.CONNECT) {
        viewModel.selectTab(AetherTab.CONNECT)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val targetNode = pendingNodeToConnect ?: uiState.selectedNode
        viewModel.startVpnServiceWithNode(
            context = context,
            node = targetNode,
            isManualSelection = pendingIsManualSelection
        )
        pendingNodeToConnect = null
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val targetNode = pendingNodeToConnect ?: uiState.selectedNode
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            AetherVpnStateBus.appendLog(
                tag = "OS",
                message = "Android VpnService user consent granted.",
                isHighlight = true
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.startVpnServiceWithNode(
                    context = context,
                    node = targetNode,
                    isManualSelection = pendingIsManualSelection
                )
                pendingNodeToConnect = null
            }
        } else {
            viewModel.startVpnServiceWithNode(
                context = context,
                node = targetNode,
                isManualSelection = pendingIsManualSelection
            )
            pendingNodeToConnect = null
        }
    }

    val initiateVpnConnect: (VpnNode, Boolean) -> Unit = { nodeToConnect, isManual ->
        pendingNodeToConnect = nodeToConnect
        pendingIsManualSelection = isManual
        val prepareIntent = try {
            VpnService.prepare(context)
        } catch (_: Exception) {
            null
        }
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnServiceWithNode(
                context = context,
                node = nodeToConnect,
                isManualSelection = isManual
            )
            pendingNodeToConnect = null
        }
    }

    // Auto-dismiss full-screen glass event popup after 2.6 seconds (or tap anywhere)
    LaunchedEffect(uiState.activePopup?.id) {
        if (uiState.activePopup != null) {
            delay(2600L)
            viewModel.dismissPopup()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated Liquid iPhone Glass Mesh Backdrop (smooth Dark/Light transition)
        IosMeshBackdrop(isDark = uiState.preferences.isDarkTheme)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Continuous 60 FPS GIF-Style Animated "A" Logo
                            AnimatedGifStyleAVpnLogo(
                                size = 40.dp,
                                cornerRadius = 12.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                                Text(
                                    text = "Ultra-Fast Tunnels • By Ashir ♥️",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // Tappable "Ashir ♥️" Developer Badge
                        Surface(
                            onClick = { viewModel.setShowDeveloperCreditsModal(true) },
                            shape = RoundedCornerShape(50),
                            color = FireCrimson.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, FireCrimson.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("topbar_ashir_credits_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Developer Credits",
                                    tint = FireCrimson,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ashir",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Smooth Animated Sun / Moon Dark Mode Pill Toggle
                        AnimatedGlassThemeToggle(
                            isDark = uiState.preferences.isDarkTheme,
                            onToggle = {
                                viewModel.toggleDarkTheme(!uiState.preferences.isDarkTheme)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                val navBarColor by animateColorAsState(
                    targetValue = MaterialTheme.colorScheme.surface.copy(
                        alpha = if (uiState.preferences.isDarkTheme) 0.80f else 0.88f
                    ),
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "nav_bar_glass_color"
                )

                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = navBarColor,
                    tonalElevation = 0.dp
                ) {
                    AetherTab.entries.forEach { tab ->
                        val selected = uiState.activeTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AetherTab.CONNECT -> if (selected) Icons.Filled.PowerSettingsNew else Icons.Outlined.PowerSettingsNew
                                        AetherTab.NODES -> if (selected) Icons.Filled.Public else Icons.Outlined.Public
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (uiState.activeTab) {
                    AetherTab.CONNECT -> {
                        ConnectScreen(
                            uiState = uiState,
                            onToggleConnection = {
                                val currentStatus = uiState.telemetry.status
                                if (currentStatus == TunnelConnectionStatus.CONNECTED ||
                                    currentStatus == TunnelConnectionStatus.CONNECTING
                                ) {
                                    viewModel.stopVpnService(context)
                                } else {
                                    initiateVpnConnect(
                                        uiState.selectedNode,
                                        !uiState.isAutoFastestMode
                                    )
                                }
                            },
                            onConnectSmartFastest = {
                                // Directly fetch latest & fastest live server available right now and connect!
                                viewModel.fetchLatestAndConnectFastestNow { fastestNow ->
                                    initiateVpnConnect(fastestNow, false)
                                }
                            },
                            onOpenNodeSelector = {
                                viewModel.selectTab(AetherTab.NODES)
                            },
                            onOpenDeveloperCredits = {
                                viewModel.setShowDeveloperCreditsModal(true)
                            }
                        )
                    }

                    AetherTab.NODES -> {
                        NodeListScreen(
                            uiState = uiState,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSelectRegion = viewModel::selectRegion,
                            onSelectSortOption = viewModel::selectSortOption,
                            onTogglePingSort = viewModel::togglePingSortOrder,
                            onSelectAutoFastest = {
                                viewModel.fetchLatestAndConnectFastestNow { fastestNow ->
                                    initiateVpnConnect(fastestNow, false)
                                }
                            },
                            onSelectAndConnectNode = { node ->
                                val chosenNode = node.copy(protocol = uiState.preferences.preferredProtocol)
                                viewModel.selectManualNode(chosenNode)
                                initiateVpnConnect(chosenNode, true)
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onSyncPublicApi = { viewModel.syncLivePublicNodes(silent = false) },
                            onOpenAddCustomModal = { viewModel.setShowAddCustomNodeModal(true) },
                            onOpenDeveloperCredits = { viewModel.setShowDeveloperCreditsModal(true) }
                        )
                    }
                }
            }
        }

        // Full-Screen Optimized iPhone Glass Event Popup Overlay (Centered on Whole Screen!)
        AnimatedVisibility(
            visible = uiState.activePopup != null,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.86f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            uiState.activePopup?.let { popup ->
                AVpnFullScreenGlassPopupOverlay(
                    popup = popup,
                    isDark = uiState.preferences.isDarkTheme,
                    onDismiss = viewModel::dismissPopup
                )
            }
        }

        // Full-Screen Optimized Developer Credits Glass Overlay
        AnimatedVisibility(
            visible = uiState.showDeveloperCreditsModal,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.88f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            AshirDeveloperCreditsFullScreenOverlay(
                isDark = uiState.preferences.isDarkTheme,
                onDismiss = { viewModel.setShowDeveloperCreditsModal(false) }
            )
        }

        // Full-Screen Optimized Add Custom Tunnel Glass Overlay
        AnimatedVisibility(
            visible = uiState.showAddCustomNodeModal,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.88f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            AddCustomNodeFullScreenOverlay(
                isDark = uiState.preferences.isDarkTheme,
                onDismiss = { viewModel.setShowAddCustomNodeModal(false) },
                onConfirmAdd = { country, code, city, ip, port, operator ->
                    viewModel.addCustomCommunityNode(
                        countryName = country,
                        countryCode = code,
                        cityName = city,
                        ipAddress = ip,
                        port = port,
                        operatorName = operator,
                        protocol = uiState.preferences.preferredProtocol
                    )
                }
            )
        }

        // Animated GIF-Style Boot Splash Screen with "A" VPN Logo & "Made with love by Ashir ♥️"
        AnimatedVisibility(
            visible = showStartupSplash,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(480))
        ) {
            AVpnAnimatedBootScreen(
                progress = bootProgress,
                onDismiss = { showStartupSplash = false }
            )
        }
    }
}

/**
 * Full-Screen Optimized iPhone Glass Event Popup Overlay.
 * Covers the whole screen with a soft translucent scrim and centers a glowing glass card
 * with GIF-style logo, flag badge, tunnel status, and "Made with love by Ashir ♥️".
 */
@Composable
private fun AVpnFullScreenGlassPopupOverlay(
    popup: VpnEventPopup,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val accentColor = when (popup.tone) {
        VpnPopupTone.CONNECTED -> EmeraldConnected
        VpnPopupTone.CONNECTING -> FireOrange
        VpnPopupTone.DISCONNECTED -> FireCrimson
        VpnPopupTone.THEME -> SkyBluePrimary
        VpnPopupTone.INFO -> SkyBluePrimary
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 24.dp)
            .testTag("vpn_event_popup_card"),
        contentAlignment = Alignment.Center
    ) {
        IosGlassCard(
            isDark = isDark,
            cornerRadius = 30.dp,
            accentGlowColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Animated GIF-Style Logo + Country/Event Emblem
                Box(contentAlignment = Alignment.BottomEnd) {
                    AnimatedGifStyleAVpnLogo(
                        size = 72.dp,
                        cornerRadius = 20.dp
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, accentColor),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = popup.flagEmoji,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = popup.detailBadge,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = popup.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = popup.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Developer Credit Badge inside Popup
                Surface(
                    shape = RoundedCornerShape(50),
                    color = FireCrimson.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, FireCrimson.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = FireCrimson,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = popup.badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dismiss_event_popup_button")
                ) {
                    Text(
                        text = "Awesome",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

/**
 * Smooth Animated Sun/Moon iPhone Glass Pill Toggle for Dark/Light Mode.
 */
@Composable
private fun AnimatedGlassThemeToggle(
    isDark: Boolean,
    onToggle: () -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (isDark) 26.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "theme_thumb_offset"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (isDark) 360f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "theme_icon_rotation"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isDark) {
            Color(0xFF182844).copy(alpha = 0.90f)
        } else {
            Color.White.copy(alpha = 0.85f)
        },
        animationSpec = tween(450),
        label = "theme_track_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isDark) {
            SkyBluePrimary.copy(alpha = 0.55f)
        } else {
            FireGold.copy(alpha = 0.65f)
        },
        animationSpec = tween(450),
        label = "theme_border_color"
    )

    Box(
        modifier = Modifier
            .width(62.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .testTag("theme_toggle_button"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = null,
                tint = FireGold.copy(alpha = if (isDark) 0.35f else 0f),
                modifier = Modifier.size(14.dp)
            )
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = SkyBluePrimary.copy(alpha = if (isDark) 0f else 0.45f),
                modifier = Modifier.size(14.dp)
            )
        }

        Surface(
            shape = CircleShape,
            color = if (isDark) SkyBluePrimary else FireOrange,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(26.dp)
                .offset(x = thumbOffset)
                .rotate(iconRotation)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Toggle light or dark theme",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Liquid iPhone Glass Mesh Gradient Backdrop with animated drifting ambient orbs
 * and smooth 520ms Dark/Light mode color transitions.
 */
@Composable
private fun IosMeshBackdrop(isDark: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF070D19) else Color(0xFFEAF2FF),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "backdrop_base_color"
    )
    val orb1Color by animateColorAsState(
        targetValue = if (isDark) {
            SkyBluePrimary.copy(alpha = 0.24f)
        } else {
            SkyBluePrimary.copy(alpha = 0.26f)
        },
        animationSpec = tween(520),
        label = "orb1_color"
    )
    val orb2Color by animateColorAsState(
        targetValue = if (isDark) {
            FireOrange.copy(alpha = 0.18f)
        } else {
            FireOrange.copy(alpha = 0.16f)
        },
        animationSpec = tween(520),
        label = "orb2_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mesh_drift")
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_drift_phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        val driftX = cos(driftPhase) * (size.width * 0.06f)
        val driftY = sin(driftPhase) * (size.height * 0.04f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orb1Color, Color.Transparent),
                center = Offset(size.width * 0.20f + driftX, size.height * 0.15f + driftY),
                radius = size.minDimension * 0.75f
            ),
            radius = size.minDimension * 0.75f,
            center = Offset(size.width * 0.20f + driftX, size.height * 0.15f + driftY)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orb2Color, Color.Transparent),
                center = Offset(size.width * 0.82f - driftX, size.height * 0.45f - driftY),
                radius = size.minDimension * 0.70f
            ),
            radius = size.minDimension * 0.70f,
            center = Offset(size.width * 0.82f - driftX, size.height * 0.45f - driftY)
        )
    }
}

/**
 * Animated GIF-Style Boot Splash Screen featuring:
 * - Multi-ring counter-rotating neon cyber-fire vortex & orbital arcs
 * - 18 rising glowing plasma/fire embers
 * - 60 FPS `AnimatedGifStyleAVpnLogo` in the center
 * - Live animated progress bar & prominent "Made with love by Ashir ♥️" Developer Card
 */
@Composable
private fun AVpnAnimatedBootScreen(
    progress: Float,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gif_boot_anim")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "boot_outer_rot"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "boot_inner_rot"
    )

    val flameWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "boot_flame_wave"
    )

    val logoPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boot_logo_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050A14),
                        Color(0xFF0D1B35),
                        Color(0xFF060B16)
                    )
                )
            )
            .testTag("startup_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0 until 18) {
                val xRatio = ((i * 37) % 100) / 100f
                val cycle = ((flameWave / (2f * PI.toFloat())) + (i * 0.11f)) % 1f
                val yPos = h * (1f - cycle)
                val xWobble = sin(flameWave + i) * 18.dp.toPx()
                val alpha = (sin(cycle * PI.toFloat()) * 0.65f).coerceIn(0f, 1f)

                drawCircle(
                    color = if (i % 2 == 0) FireOrange.copy(alpha = alpha) else SkyBluePrimary.copy(alpha = alpha),
                    radius = (3f + (i % 4)).dp.toPx(),
                    center = Offset(w * xRatio + xWobble, yPos)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(210.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                FireOrange.copy(alpha = 0.42f),
                                SkyBluePrimary.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxR
                        ),
                        radius = maxR,
                        center = center
                    )

                    val outerArcRadius = maxR * 0.88f
                    val outerTopLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius)
                    val outerSize = Size(outerArcRadius * 2f, outerArcRadius * 2f)
                    for (seg in 0 until 3) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(FireGold, FireOrange, FireCrimson, SkyBluePrimary, FireGold)
                            ),
                            startAngle = outerRotation + (seg * 120f),
                            sweepAngle = 78f,
                            useCenter = false,
                            topLeft = outerTopLeft,
                            size = outerSize,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    val innerArcRadius = maxR * 0.74f
                    val innerTopLeft = Offset(center.x - innerArcRadius, center.y - innerArcRadius)
                    val innerSize = Size(innerArcRadius * 2f, innerArcRadius * 2f)
                    for (seg in 0 until 4) {
                        drawArc(
                            color = SkyBluePrimary.copy(alpha = 0.85f),
                            startAngle = innerRotation + (seg * 90f),
                            sweepAngle = 48f,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = innerSize,
                            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    for (orbIdx in 0 until 6) {
                        val angleRad = ((outerRotation + orbIdx * 60f) * PI / 180.0).toFloat()
                        val ox = center.x + cos(angleRad) * outerArcRadius
                        val oy = center.y + sin(angleRad) * outerArcRadius
                        drawCircle(
                            color = if (orbIdx % 2 == 0) FireGold else SkyBluePrimary,
                            radius = 5.dp.toPx(),
                            center = Offset(ox, oy)
                        )
                    }
                }

                // 60 FPS Animated GIF-Style "A VPN" Logo
                AnimatedGifStyleAVpnLogo(
                    size = 114.dp,
                    cornerRadius = 28.dp,
                    modifier = Modifier.scale(logoPulse)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = FireOrange,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    progress < 0.45f -> "Igniting Ultra-Fast Tunnel Engine…"
                    progress < 0.80f -> "Fetching Fastest Worldwide Servers…"
                    else -> "Ready • Engineered by Ashir ♥️"
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.82f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = FireOrange,
                trackColor = Color.White.copy(alpha = 0.16f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        // Bottom Glass Developer Credit Card: "Made with love by Ashir ♥️"
        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(FireGold.copy(alpha = 0.7f), SkyBluePrimary.copy(alpha = 0.6f))
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp, start = 24.dp, end = 24.dp)
                .testTag("splash_made_by_ashir_badge")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.made_by_ashir),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Founder & Lead Developer • Tap to Skip",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }
    }
}

/**
 * Full-Screen Optimized Developer Credits Overlay showcasing Ashir ♥️.
 */
@Composable
private fun AshirDeveloperCreditsFullScreenOverlay(
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        IosGlassCard(
            isDark = isDark,
            cornerRadius = 30.dp,
            accentGlowColor = FireOrange,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedGifStyleAVpnLogo(
                    size = 76.dp,
                    cornerRadius = 22.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Created by Ashir ♥️",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Founder & Lead Architect of A VPN",
                    style = MaterialTheme.typography.labelLarge,
                    color = FireOrange
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Crafted with love by Ashir ♥️ to deliver ultra-fast worldwide VPN tunnels, zero-downtime WhatsApp & full-app support, and a lightweight iPhone Glass UI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SkyBluePrimary.copy(alpha = 0.14f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "100% App Uptime",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SkyBluePrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = FireOrange.copy(alpha = 0.14f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = FireOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "By Ashir ♥️",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = FireOrange
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = FireOrange),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("close_developer_credits_button")
                ) {
                    Text(
                        text = "Awesome ♥️",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

/**
 * Full-Screen Optimized Add Custom Tunnel Glass Overlay.
 */
@Composable
private fun AddCustomNodeFullScreenOverlay(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onConfirmAdd: (country: String, code: String, city: String, ip: String, port: Int, operator: String) -> Unit
) {
    var countryName by remember { mutableStateOf("Finland") }
    var countryCode by remember { mutableStateOf("FI") }
    var cityName by remember { mutableStateOf("Helsinki") }
    var ipAddress by remember { mutableStateOf("95.216.144.18") }
    var portText by remember { mutableStateOf("443") }
    var operatorName by remember { mutableStateOf("Custom Ultra Tunnel") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        IosGlassCard(
            isDark = isDark,
            cornerRadius = 28.dp,
            accentGlowColor = SkyBluePrimary,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 390.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Custom Ultra Tunnel",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryName,
                        onValueChange = { countryName = it },
                        label = { Text("Country") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_country_input")
                    )
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.take(2) },
                        label = { Text("ISO") },
                        singleLine = true,
                        modifier = Modifier
                            .width(78.dp)
                            .testTag("custom_node_iso_input")
                    )
                }
                OutlinedTextField(
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("City / Hub") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_node_city_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Public IP / Host") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_ip_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier
                            .width(92.dp)
                            .testTag("custom_node_port_input")
                    )
                }
                OutlinedTextField(
                    value = operatorName,
                    onValueChange = { operatorName = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_node_operator_input")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("cancel_add_custom_node_button")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val parsedPort = portText.toIntOrNull() ?: 443
                            onConfirmAdd(countryName, countryCode, cityName, ipAddress, parsedPort, operatorName)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("confirm_add_custom_node_button")
                    ) {
                        Text("Add & Connect", color = Color.White)
                    }
                }
            }
        }
    }
}
