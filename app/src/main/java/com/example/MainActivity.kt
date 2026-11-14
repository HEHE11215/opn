package com.example

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon as AndroidDrawableIcon
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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RelayTunnelType
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnNode
import com.example.ui.AetherTab
import com.example.ui.AnimatedGifStyleAVpnLogo
import com.example.ui.ConnectScreen
import com.example.ui.ConnectionLogScreen
import com.example.ui.IosGlassCard
import com.example.ui.NodeListScreen
import com.example.ui.VpnEventPopup
import com.example.ui.VpnPopupTone
import com.example.ui.VpnServiceStateIndicatorBar
import com.example.ui.VpnUiState
import com.example.ui.VpnViewModel
import com.example.ui.theme.AetherNodeTheme
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.SkyBluePrimary
import com.example.vpn.AVpnQuickTileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val fastStartRequestFlow = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkFastStartIntent(intent)
        enableEdgeToEdge()
        setContent {
            val uiState by vpnViewModel.uiState.collectAsStateWithLifecycle()
            val fastStartTrigger by fastStartRequestFlow.collectAsStateWithLifecycle()

            AetherNodeTheme(darkTheme = uiState.preferences.isDarkTheme) {
                AVpnRootApp(
                    uiState = uiState,
                    viewModel = vpnViewModel,
                    fastStartTrigger = fastStartTrigger
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkFastStartIntent(intent)
    }

    private fun checkFastStartIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FAST_START_CONNECT, false) == true ||
            intent?.getStringExtra(EXTRA_FAST_START_CONNECT) == "true"
        ) {
            intent.removeExtra(EXTRA_FAST_START_CONNECT)
            fastStartRequestFlow.value = System.currentTimeMillis()
        }
    }

    companion object {
        const val EXTRA_FAST_START_CONNECT = "extra_fast_start_connect"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVpnRootApp(
    uiState: VpnUiState,
    viewModel: VpnViewModel,
    fastStartTrigger: Long = 0L
) {
    val context = LocalContext.current
    var showStartupSplash by rememberSaveable { mutableStateOf(fastStartTrigger == 0L) }
    var bootProgress by remember { mutableFloatStateOf(0.08f) }
    var pendingNodeToConnect by remember { mutableStateOf<VpnNode?>(null) }
    var pendingIsManualSelection by remember { mutableStateOf(false) }

    // Minimalist Whole-App Theme Wave Transition State
    val isDark = uiState.preferences.isDarkTheme
    var previousDarkState by remember { mutableStateOf(isDark) }
    val themeWaveProgress = remember { Animatable(1f) }
    var showMinimalThemePill by remember { mutableStateOf(false) }

    LaunchedEffect(isDark) {
        if (isDark != previousDarkState) {
            previousDarkState = isDark
            showMinimalThemePill = true
            themeWaveProgress.snapTo(0f)
            themeWaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
            delay(950L)
            showMinimalThemePill = false
        }
    }

    // Ultra-fast lightweight boot progression (~400ms total)
    LaunchedEffect(showStartupSplash) {
        if (showStartupSplash) {
            val steps = 8
            for (i in 1..steps) {
                bootProgress = i / steps.toFloat()
                delay(45L)
            }
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
            pendingNodeToConnect = null
            viewModel.showEventPopup(
                title = "VPN Permission Needed",
                subtitle = "Allow Android VPN connection permission to activate the tunnel.",
                flagEmoji = "🛡️",
                detailBadge = "System Consent Required",
                tone = VpnPopupTone.DISCONNECTED
            )
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

    // Handle 1-Tap Fast Connect launched from Top Status Bar Notification / Quick Settings Tile / Shortcut
    LaunchedEffect(fastStartTrigger) {
        if (fastStartTrigger > 0L) {
            showStartupSplash = false
            viewModel.consumePendingStartupConnect()
            val fastestNode = viewModel.enableAutoFastestModeAndGetNode()
            initiateVpnConnect(fastestNode, false)
        }
    }

    // Handle Auto-Connect on Startup using last selected server when enabled
    val pendingStartupConnect by viewModel.pendingStartupConnect.collectAsStateWithLifecycle()
    LaunchedEffect(pendingStartupConnect, fastStartTrigger) {
        val pending = pendingStartupConnect
        if (pending != null && fastStartTrigger == 0L) {
            showStartupSplash = false
            viewModel.consumePendingStartupConnect()
            initiateVpnConnect(pending.first, pending.second)
        }
    }

    val requestAddQuickSettingsTile: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            if (statusBarManager != null) {
                statusBarManager.requestAddTileService(
                    ComponentName(context, AVpnQuickTileService::class.java),
                    context.getString(R.string.qs_tile_label),
                    AndroidDrawableIcon.createWithResource(context, R.drawable.ic_avpn_tile),
                    context.mainExecutor
                ) { result ->
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                            viewModel.showEventPopup(
                                title = "Quick Tile Added!",
                                subtitle = "Pull down your notification shade anytime to toggle A VPN.",
                                flagEmoji = "⚡",
                                detailBadge = "Quick Settings Ready",
                                tone = VpnPopupTone.CONNECTED
                            )
                        }
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                            viewModel.showEventPopup(
                                title = "Quick Tile Already Active",
                                subtitle = "A VPN toggle is already in your Quick Settings notification shade.",
                                flagEmoji = "🛡️",
                                detailBadge = "Already Added",
                                tone = VpnPopupTone.INFO
                            )
                        }
                    }
                }
            }
        } else {
            viewModel.showEventPopup(
                title = "Add From Quick Settings",
                subtitle = "Swipe down your notification shade, tap Edit (pencil), and drag A VPN into active tiles.",
                flagEmoji = "⚙️",
                detailBadge = "Quick Settings Tile",
                tone = VpnPopupTone.INFO
            )
        }
    }

    // Auto-dismiss full-screen glass event popup after 2.5 seconds (or tap anywhere)
    LaunchedEffect(uiState.activePopup?.id) {
        if (uiState.activePopup != null) {
            delay(2500L)
            viewModel.dismissPopup()
        }
    }

    // Whole-App Minimalist Scale & Glass Transition on Dark/Light Switch
    val wholeAppScale = if (themeWaveProgress.value < 1f) {
        0.985f + 0.015f * sin(themeWaveProgress.value * PI.toFloat())
    } else {
        1f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Enhanced Liquid iPhone Frosted Glass Mesh Backdrop
        IosMeshBackdrop(isDark = isDark)

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .scale(wholeAppScale),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Exact 3D "A" Shield App Logo with 60 FPS GIF-Style Animation
                            AnimatedGifStyleAVpnLogo(
                                size = 38.dp,
                                cornerRadius = 11.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "TUN0 • By Ashir ♥️",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // Compact "Ashir ♥️" Developer Pill
                        Surface(
                            onClick = { viewModel.setShowDeveloperCreditsModal(true) },
                            shape = RoundedCornerShape(50),
                            color = FireCrimson.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, FireCrimson.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(32.dp)
                                .testTag("topbar_ashir_credits_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Developer Credits",
                                    tint = FireCrimson,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Ashir",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Minimalist Glass Dark / Light UI Switch
                        MinimalistGlassThemeSwitch(
                            isDark = isDark,
                            onToggle = {
                                viewModel.toggleDarkTheme(!isDark)
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
                        alpha = if (isDark) 0.78f else 0.86f
                    ),
                    animationSpec = tween(480, easing = FastOutSlowInEasing),
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
                                        AetherTab.LOGS -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Live VpnService Connection State Visual Indicator (tapping opens Connection Log)
                VpnServiceStateIndicatorBar(
                    telemetry = uiState.telemetry,
                    isDark = isDark,
                    onClick = { viewModel.selectTab(AetherTab.LOGS) },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                                    viewModel.fetchLatestAndConnectFastestNow { fastestNow ->
                                        initiateVpnConnect(fastestNow, false)
                                    }
                                },
                                onOpenNodeSelector = {
                                    viewModel.selectTab(AetherTab.NODES)
                                },
                                onClearDiagnostics = viewModel::clearDiagnostics,
                                onOpenConnectionLogs = {
                                    viewModel.selectTab(AetherTab.LOGS)
                                },
                                onOpenDeveloperCredits = {
                                    viewModel.setShowDeveloperCreditsModal(true)
                                },
                                onToggleWifiOnlyLatency = viewModel::setWifiOnlyLatencyCheck,
                                onToggleAutoConnectOnStartup = viewModel::setAutoConnectOnStartup,
                                onRequestAddQuickTile = requestAddQuickSettingsTile
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

                        AetherTab.LOGS -> {
                            ConnectionLogScreen(
                                telemetry = uiState.telemetry,
                                isDark = isDark,
                                onClearLogs = viewModel::clearDiagnostics,
                                onRetryFastSwitch = {
                                    viewModel.fetchLatestAndConnectFastestNow { fastestNow ->
                                        initiateVpnConnect(fastestNow, false)
                                    }
                                },
                                onBrowseServers = {
                                    viewModel.selectTab(AetherTab.NODES)
                                },
                                onCopiedNotice = { summary ->
                                    viewModel.showEventPopup(
                                        title = "Connection Log Copied",
                                        subtitle = summary,
                                        flagEmoji = "📋",
                                        detailBadge = "Clipboard Ready",
                                        tone = VpnPopupTone.INFO
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Minimalist Whole-App Theme Radial Wave & Floating Pill when switching Dark/Light
        if (themeWaveProgress.value < 1f) {
            val waveProgress = themeWaveProgress.value
            val waveAlpha = (1f - waveProgress) * 0.35f
            val waveColor = if (isDark) SkyBluePrimary else FireGold
            Canvas(modifier = Modifier.fillMaxSize()) {
                val origin = Offset(size.width * 0.90f, size.height * 0.07f)
                val maxRadius = size.maxDimension * 1.25f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            waveColor.copy(alpha = waveAlpha),
                            waveColor.copy(alpha = waveAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = origin,
                        radius = maxRadius * waveProgress.coerceAtLeast(0.05f)
                    ),
                    radius = maxRadius * waveProgress,
                    center = origin
                )
            }
        }

        AnimatedVisibility(
            visible = showMinimalThemePill,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 92.dp)
        ) {
            IosGlassCard(
                isDark = isDark,
                cornerRadius = 50.dp,
                accentGlowColor = if (isDark) SkyBluePrimary else FireGold
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = if (isDark) SkyBluePrimary else FireOrange,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDark) "Dark Glass UI" else "Light Glass UI",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Full-Screen Optimized iPhone Glass Event Popup Overlay
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
                    isDark = isDark,
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
                isDark = isDark,
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
                isDark = isDark,
                onDismiss = { viewModel.setShowAddCustomNodeModal(false) },
                onConfirmAdd = { country, code, city, ip, port, operator, relayType, username, password ->
                    viewModel.addCustomCommunityNode(
                        countryName = country,
                        countryCode = code,
                        cityName = city,
                        ipAddress = ip,
                        port = port,
                        operatorName = operator,
                        protocol = uiState.preferences.preferredProtocol,
                        relayType = relayType,
                        username = username,
                        password = password
                    )
                }
            )
        }

        // Animated GIF-Style Boot Splash Screen with "A" VPN Logo & "Made with love by Ashir ♥️"
        AnimatedVisibility(
            visible = showStartupSplash,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(420))
        ) {
            AVpnAnimatedBootScreen(
                progress = bootProgress,
                onDismiss = { showStartupSplash = false }
            )
        }
    }
}

/**
 * Minimalist iOS Glass Dark/Light UI Switch with smooth sliding frosted thumb,
 * rotating sun/moon icon, and compact UI label.
 */
@Composable
private fun MinimalistGlassThemeSwitch(
    isDark: Boolean,
    onToggle: () -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (isDark) 34.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "theme_thumb_offset"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "theme_icon_rotation"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isDark) {
            Color(0xFF13223A).copy(alpha = 0.88f)
        } else {
            Color.White.copy(alpha = 0.88f)
        },
        animationSpec = tween(420),
        label = "theme_track_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isDark) {
            SkyBluePrimary.copy(alpha = 0.55f)
        } else {
            FireGold.copy(alpha = 0.65f)
        },
        animationSpec = tween(420),
        label = "theme_border_color"
    )

    Box(
        modifier = Modifier
            .width(68.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(1.1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .testTag("theme_toggle_button"),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (isDark) "Dark" else "Light",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            color = if (isDark) SkyBluePrimary else FireOrange,
            modifier = Modifier
                .align(if (isDark) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 6.dp)
        )

        Surface(
            shape = CircleShape,
            color = if (isDark) SkyBluePrimary else FireOrange,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(25.dp)
                .offset(x = thumbOffset)
                .rotate(iconRotation)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Toggle light or dark theme",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Full-Screen Optimized iPhone Glass Event Popup Overlay.
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
            .background(Color.Black.copy(alpha = 0.50f))
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
            cornerRadius = 28.dp,
            accentGlowColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 350.dp)
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AnimatedGifStyleAVpnLogo(
                        size = 68.dp,
                        cornerRadius = 19.dp
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, accentColor),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = popup.flagEmoji,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = popup.detailBadge,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = popup.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = popup.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = FireCrimson.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, FireCrimson.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = FireCrimson,
                            modifier = Modifier.size(12.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("dismiss_event_popup_button")
                ) {
                    Text(
                        text = "OK",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Ultra-Light Static Frosted Glass Mesh Backdrop (zero infinite frame redraws).
 */
@Composable
private fun IosMeshBackdrop(isDark: Boolean) {
    val bgBrush = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF050A14),
                    Color(0xFF0B162C),
                    Color(0xFF060B15)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE5F0FF),
                    Color(0xFFF3F8FF),
                    Color(0xFFEAF2FC)
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    )
}

/**
 * Lightweight Boot Screen featuring the 3D "A" Shield Logo & "Made with love by Ashir ♥️".
 */
@Composable
private fun AVpnAnimatedBootScreen(
    progress: Float,
    onDismiss: () -> Unit
) {
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            AnimatedGifStyleAVpnLogo(
                size = 112.dp,
                cornerRadius = 28.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = FireOrange,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    progress < 0.45f -> "Starting Wi-Fi Warp Engine…"
                    progress < 0.80f -> "Syncing Ultra-Light Tunnels…"
                    else -> "Ready • Engineered by Ashir ♥️"
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.82f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = FireOrange,
                trackColor = Color.White.copy(alpha = 0.16f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .width(190.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        // Bottom Glass Developer Credit Card: "Made with love by Ashir ♥️"
        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(22.dp),
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
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.made_by_ashir),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
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
            .background(Color.Black.copy(alpha = 0.54f))
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
            cornerRadius = 28.dp,
            accentGlowColor = FireOrange,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 370.dp)
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedGifStyleAVpnLogo(
                    size = 72.dp,
                    cornerRadius = 20.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Created by Ashir ♥️",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Founder & Lead Architect of A VPN",
                    style = MaterialTheme.typography.labelLarge,
                    color = FireOrange
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Crafted with love by Ashir ♥️ to deliver native Wi-Fi speed tunnels, Top Status Bar 1-tap controls, and an aesthetic iOS Frosted Glass UI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Wi-Fi Warp",
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
                                modifier = Modifier.size(15.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = FireOrange),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("close_developer_credits_button")
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
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
    onConfirmAdd: (
        country: String,
        code: String,
        city: String,
        ip: String,
        port: Int,
        operator: String,
        relayType: RelayTunnelType,
        username: String,
        password: String
    ) -> Unit
) {
    var countryName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("1080") }
    var operatorName by remember { mutableStateOf("") }
    var selectedRelayType by remember { mutableStateOf(RelayTunnelType.SOCKS5) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
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
            cornerRadius = 26.dp,
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Custom / Pro Server",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Protocol Type Switcher: SOCKS5 vs HTTPS CONNECT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(RelayTunnelType.SOCKS5, RelayTunnelType.HTTP_CONNECT).forEach { type ->
                        val active = selectedRelayType == type
                        Surface(
                            onClick = {
                                selectedRelayType = type
                                if (portText == "1080" && type == RelayTunnelType.HTTP_CONNECT) {
                                    portText = "8080"
                                } else if (portText == "8080" && type == RelayTunnelType.SOCKS5) {
                                    portText = "1080"
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) SkyBluePrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.1.dp,
                                if (active) SkyBluePrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("custom_proto_${type.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = type.badgeLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (active) SkyBluePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryName,
                        onValueChange = { countryName = it },
                        label = { Text("Country") },
                        placeholder = { Text("Germany") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_country_input")
                    )
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.take(2).uppercase() },
                        label = { Text("ISO") },
                        placeholder = { Text("DE") },
                        singleLine = true,
                        modifier = Modifier
                            .width(78.dp)
                            .testTag("custom_node_iso_input")
                    )
                }
                OutlinedTextField(
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("City / Location") },
                    placeholder = { Text("Frankfurt") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_node_city_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("IP / Host") },
                        placeholder = { Text("1.2.3.4") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (Opt)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_username_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (Opt)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_password_input")
                    )
                }
                OutlinedTextField(
                    value = operatorName,
                    onValueChange = { operatorName = it },
                    label = { Text("Label (Optional)") },
                    placeholder = { Text("Private Pro Server") },
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("cancel_add_custom_node_button")
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            val parsedPort = portText.toIntOrNull() ?: 1080
                            onConfirmAdd(
                                countryName,
                                countryCode,
                                cityName,
                                ipAddress,
                                parsedPort,
                                operatorName,
                                selectedRelayType,
                                username,
                                password
                            )
                        },
                        enabled = ipAddress.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("confirm_add_custom_node_button")
                    ) {
                        Text(
                            text = "Verify & Add",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
