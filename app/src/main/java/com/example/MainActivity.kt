package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon as AndroidDrawableIcon
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RelayTunnelType
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnNode
import com.example.ui.AetherTab
import com.example.ui.AnimatedGifStyleAVpnLogo
import com.example.ui.ConnectScreen
import com.example.ui.ConnectionLogScreen
import com.example.ui.M3SectionCard
import com.example.ui.NodeListScreen
import com.example.ui.VpnEventPopup
import com.example.ui.VpnPopupTone
import com.example.ui.VpnServiceStateIndicatorBar
import com.example.ui.VpnUiState
import com.example.ui.VpnViewModel
import com.example.ui.theme.AetherNodeTheme
import com.example.ui.theme.AmberHandshaking
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseAlert
import com.example.vpn.AVpnQuickTileService
import com.example.vpn.EphemeralAccountProvisioner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * MainActivity for **A VPN** — Developed by Ashir ♥️.
 * Lightweight Material 3 Expressive UI with single-server lock, Material 3 Expressive
 * motion-graphics startup intro, and instantaneous Day/Night theme switching.
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
    var pendingNodeToConnect by remember { mutableStateOf<VpnNode?>(null) }
    var pendingIsManualSelection by remember { mutableStateOf(false) }

    // Material 3 Expressive Motion-Graphics Startup Intro (shown once per cold launch, ~600ms)
    var showStartupMotion by rememberSaveable { mutableStateOf(fastStartTrigger == 0L) }
    LaunchedEffect(fastStartTrigger) {
        if (fastStartTrigger > 0L) {
            showStartupMotion = false
        }
    }

    val isDark = uiState.preferences.isDarkTheme

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

    // Auto-dismiss event popup after 2.2 seconds
    LaunchedEffect(uiState.activePopup?.id) {
        if (uiState.activePopup != null) {
            delay(2200L)
            viewModel.dismissPopup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = stringResource(R.string.made_by_ashir),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("developer_credit_once_label")
                                )
                            }
                        }
                    },
                    actions = {
                        // Seamless Material 3 Day / Night Switch Button
                        MaterialDayNightToggle(
                            isDark = isDark,
                            onToggle = {
                                viewModel.toggleDarkTheme(!isDark)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface,
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
                        .padding(horizontal = 16.dp, vertical = 6.dp)
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
                                onToggleWifiOnlyLatency = viewModel::setWifiOnlyLatencyCheck,
                                onToggleAutoConnectOnStartup = viewModel::setAutoConnectOnStartup,
                                onRequestAddQuickTile = requestAddQuickSettingsTile,
                                onRenewSshAccount = viewModel::renewEphemeralSshAccountNow,
                                onProbeSelectedLatency = { viewModel.probeSelectedNodeLatencyNow() }
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
                                onOpenAddCustomModal = { viewModel.setShowAddCustomNodeModal(true) }
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

        // Clean Material 3 Event Popup Dialog
        uiState.activePopup?.let { popup ->
            MaterialEventPopupDialog(
                popup = popup,
                onDismiss = viewModel::dismissPopup
            )
        }

        // Clean Material 3 Add Custom Server Dialog
        if (uiState.showAddCustomNodeModal) {
            AddCustomNodeDialog(
                onDismiss = { viewModel.setShowAddCustomNodeModal(false) },
                onOpenFreeSshFetcher = {
                    viewModel.setShowAddCustomNodeModal(false)
                    viewModel.setShowFreeSshFetcherModal(true)
                },
                onConfirmAdd = { country, code, city, ip, port, operator, relayType, username, password, sshPrivateKey, sniHost, udpPort ->
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
                        password = password,
                        sshPrivateKey = sshPrivateKey,
                        sniHost = sniHost,
                        udpPort = udpPort
                    )
                }
            )
        }

        // Clean Material 3 Live Free SSH / TLS / UDP Internet Fetcher & Auto-Grabber Dialog
        if (uiState.showFreeSshFetcherModal) {
            FreeSshAutoFetchDialog(
                initialFeedUrl = uiState.preferences.sshProviderFeedUrl,
                isFetching = uiState.isFetchingSshAccounts,
                captchaGatedProviders = uiState.sshCaptchaGatedProviders,
                onDismiss = { viewModel.setShowFreeSshFetcherModal(false) },
                onAutoFetchFeed = { url -> viewModel.fetchFreeSshFromInternetNow(url) },
                onImportCapturedPayload = { rawPayload, providerName ->
                    viewModel.importFreeSshAccountPayload(
                        rawPayload = rawPayload,
                        providerName = providerName,
                        connectNow = { verifiedNode ->
                            initiateVpnConnect(verifiedNode, true)
                        }
                    )
                }
            )
        }

        // Material 3 Expressive Motion-Graphics Startup Animation (plays once on cold start)
        AnimatedVisibility(
            visible = showStartupMotion,
            exit = fadeOut(animationSpec = tween(220)) + scaleOut(
                targetScale = 1.06f,
                animationSpec = tween(220)
            )
        ) {
            M3ExpressiveStartupMotionScreen(
                onFinished = { showStartupMotion = false }
            )
        }
    }
}

/**
 * Simple Material 3 Expressive Motion-Graphics Startup Screen (`M3ExpressiveStartupMotionScreen`).
 * Uses lightweight hardware-accelerated vector motion graphics (spring-physics morphing
 * squircle/pill orbits + Material 3 Expressive "A" shield monogram + "Developed by Ashir ♥️").
 * Automatically completes in ~580ms or immediately on tap.
 */
@Composable
fun M3ExpressiveStartupMotionScreen(
    onFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.68f) }
    val ringProgress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            ringProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
        delay(560L)
        onFinished()
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryColor = EmeraldConnected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished
            )
            .testTag("m3_expressive_startup_motion"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .graphicsLayer {
                    alpha = contentAlpha.value
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(168.dp)
            ) {
                // Material 3 Expressive Motion-Graphics Geometric Shapes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = ringProgress.value
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Expressive outer morphing squircle wave
                    val outerSize = size.minDimension * (0.64f + 0.34f * p)
                    rotate(degrees = 18f * p, pivot = centerOffset) {
                        drawRoundRect(
                            color = primaryContainerColor.copy(alpha = (1f - p * 0.45f).coerceIn(0f, 1f)),
                            topLeft = Offset(
                                centerOffset.x - outerSize / 2f,
                                centerOffset.y - outerSize / 2f
                            ),
                            size = Size(outerSize, outerSize),
                            cornerRadius = CornerRadius(outerSize * 0.34f, outerSize * 0.34f)
                        )
                    }

                    // Expressive secondary counter-rotating tonal frame
                    val midSize = size.minDimension * (0.56f + 0.20f * p)
                    rotate(degrees = -24f * p, pivot = centerOffset) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.22f * p),
                            topLeft = Offset(
                                centerOffset.x - midSize / 2f,
                                centerOffset.y - midSize / 2f
                            ),
                            size = Size(midSize, midSize),
                            cornerRadius = CornerRadius(midSize * 0.30f, midSize * 0.30f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Expressive emerald status accent dots in diagonal orbit
                    val orbitRadius = (size.minDimension * 0.44f) * p
                    drawCircle(
                        color = tertiaryColor.copy(alpha = 0.85f * p),
                        radius = 5.dp.toPx() * p,
                        center = Offset(
                            centerOffset.x + orbitRadius * 0.72f,
                            centerOffset.y - orbitRadius * 0.72f
                        )
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.75f * p),
                        radius = 4.dp.toPx() * p,
                        center = Offset(
                            centerOffset.x - orbitRadius * 0.72f,
                            centerOffset.y + orbitRadius * 0.72f
                        )
                    )
                }

                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
                ) {
                    AnimatedGifStyleAVpnLogo(
                        size = 86.dp,
                        cornerRadius = 26.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(R.string.made_by_ashir),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Simple Material 3 Day / Night Switch button (`MaterialDayNightToggle`).
 * Switches between Day (Light) and Night (Dark) instantaneously.
 */
@Composable
fun MaterialDayNightToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("theme_toggle_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = "Switch Day or Night theme",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isDark) "Night" else "Day",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Clean Material 3 Event Popup Dialog.
 */
@Composable
private fun MaterialEventPopupDialog(
    popup: VpnEventPopup,
    onDismiss: () -> Unit
) {
    val accentColor = when (popup.tone) {
        VpnPopupTone.CONNECTED -> EmeraldConnected
        VpnPopupTone.CONNECTING -> AmberHandshaking
        VpnPopupTone.DISCONNECTED -> RoseAlert
        VpnPopupTone.THEME -> MaterialTheme.colorScheme.primary
        VpnPopupTone.INFO -> MaterialTheme.colorScheme.primary
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 24.dp)
            .testTag("vpn_event_popup_card"),
        contentAlignment = Alignment.Center
    ) {
        M3SectionCard(
            cornerRadius = 20.dp,
            borderColor = accentColor.copy(alpha = 0.45f),
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
                        size = 64.dp,
                        cornerRadius = 18.dp
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, accentColor),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = popup.flagEmoji,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.14f)
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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = popup.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
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
 * Clean Material 3 Add Custom SSH / TLS / WS / Proxy Server Dialog.
 */
@Composable
private fun AddCustomNodeDialog(
    onDismiss: () -> Unit,
    onOpenFreeSshFetcher: () -> Unit,
    onConfirmAdd: (
        country: String,
        code: String,
        city: String,
        ip: String,
        port: Int,
        operator: String,
        relayType: RelayTunnelType,
        username: String,
        password: String,
        sshPrivateKey: String,
        sniHost: String,
        udpPort: Int
    ) -> Unit
) {
    var countryName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("443") }
    var udpPortText by remember { mutableStateOf("7300") }
    var sniHost by remember { mutableStateOf("") }
    var operatorName by remember { mutableStateOf("") }
    var selectedRelayType by remember { mutableStateOf(RelayTunnelType.SSH_TLS) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sshPrivateKey by remember { mutableStateOf("") }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        M3SectionCard(
            cornerRadius = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 410.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add SSH / TLS / Proxy Server",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = onOpenFreeSshFetcher,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("switch_to_free_ssh_fetcher_button")
                    ) {
                        Text(
                            text = "Get Free SSH",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Protocol Type Switcher: SSH TLS (443), SSH-2 (22), SSH WS (80), SOCKS5 (1080)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        RelayTunnelType.SSH_TLS,
                        RelayTunnelType.SSH_TUNNEL,
                        RelayTunnelType.SSH_WS,
                        RelayTunnelType.SOCKS5
                    ).forEach { type ->
                        val active = selectedRelayType == type
                        Surface(
                            onClick = {
                                selectedRelayType = type
                                portText = when (type) {
                                    RelayTunnelType.SSH_TLS, RelayTunnelType.HTTP_CONNECT -> "443"
                                    RelayTunnelType.SSH_TUNNEL -> "22"
                                    RelayTunnelType.SSH_WS -> "80"
                                    RelayTunnelType.SOCKS5, RelayTunnelType.SOCKS4 -> "1080"
                                    RelayTunnelType.WIREGUARD_TUNNEL -> "51820"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
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
                                        fontSize = 10.sp
                                    ),
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    softWrap = false
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
                        placeholder = { Text("Singapore") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_country_input")
                    )
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.take(2).uppercase() },
                        label = { Text("ISO") },
                        placeholder = { Text("SG") },
                        singleLine = true,
                        modifier = Modifier
                            .width(78.dp)
                            .testTag("custom_node_iso_input")
                    )
                }
                OutlinedTextField(
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("City / Provider (Opt)") },
                    placeholder = { Text("SSHOcean Singapore") },
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
                        placeholder = { Text("sg1.sshocean.net") },
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
                            .width(86.dp)
                            .testTag("custom_node_port_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sniHost,
                        onValueChange = { sniHost = it },
                        label = { Text("SNI Host (Opt)") },
                        placeholder = { Text("cdn.example.com") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_sni_input")
                    )
                    OutlinedTextField(
                        value = udpPortText,
                        onValueChange = { udpPortText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("UDP Port") },
                        singleLine = true,
                        modifier = Modifier
                            .width(96.dp)
                            .testTag("custom_node_udp_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("SSH Username") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_username_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_node_password_input")
                    )
                }
                if (selectedRelayType == RelayTunnelType.SSH_TUNNEL ||
                    selectedRelayType == RelayTunnelType.SSH_TLS ||
                    selectedRelayType == RelayTunnelType.SSH_WS
                ) {
                    OutlinedTextField(
                        value = sshPrivateKey,
                        onValueChange = { sshPrivateKey = it },
                        label = { Text("SSH Private Key PEM (Optional)") },
                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_node_key_input")
                    )
                }
                OutlinedTextField(
                    value = operatorName,
                    onValueChange = { operatorName = it },
                    label = { Text("Label (Optional)") },
                    placeholder = { Text("Fast SSH TLS") },
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
                        shape = RoundedCornerShape(10.dp),
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
                            val parsedPort = portText.toIntOrNull() ?: 443
                            val parsedUdpPort = udpPortText.toIntOrNull() ?: 7300
                            onConfirmAdd(
                                countryName,
                                countryCode,
                                cityName,
                                ipAddress,
                                parsedPort,
                                operatorName,
                                selectedRelayType,
                                username,
                                password,
                                sshPrivateKey,
                                sniHost,
                                parsedUdpPort
                            )
                        },
                        enabled = ipAddress.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("confirm_add_custom_node_button")
                    ) {
                        Text(
                            text = "Verify & Add",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Material 3 Live Internet SSH / TLS / UDP Fetcher & 1-Tap WebView Auto-Grabber Dialog.
 * Supports:
 * 1. Direct background auto-fetching from SSH JSON / `ssh://` / Base64 subscription API feeds.
 * 2. Embedded 1-tap Provider WebView with JS form auto-filler + instant DOM account extraction for
 *    Cloudflare/CAPTCHA-protected free 1-day/3-day/7-day SSH providers (`SSHOcean`, `JagoVPN`,
 *    `ServerSSH`, `VPNJantit`, `OpenTunnel`).
 * 3. Instant multi-format paste import (`ssh://user:pass@host:port`, JSON, or provider output text).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FreeSshAutoFetchDialog(
    initialFeedUrl: String,
    isFetching: Boolean,
    captchaGatedProviders: List<String>,
    onDismiss: () -> Unit,
    onAutoFetchFeed: (String) -> Unit,
    onImportCapturedPayload: (rawPayload: String, providerName: String) -> Unit
) {
    var feedUrl by remember(initialFeedUrl) { mutableStateOf(initialFeedUrl) }
    var pastePayload by remember { mutableStateOf("") }
    var activeProvider by remember { mutableStateOf<EphemeralAccountProvisioner.SshProviderPreset?>(null) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var webPageTitle by remember { mutableStateOf("") }

    BackHandler {
        if (activeProvider != null) {
            activeProvider = null
            activeWebView = null
        } else {
            onDismiss()
        }
    }

    val autoFillJs = remember {
        """
        (function() {
            var r = Math.random().toString(36).substring(2, 7);
            var u = 'avpn' + r;
            var p = 'Pass' + r + '9';
            var inputs = document.querySelectorAll('input');
            for (var i = 0; i < inputs.length; i++) {
                var el = inputs[i];
                var n = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '')).toLowerCase();
                if (el.type === 'text' && (n.indexOf('user') >= 0 || n.indexOf('login') >= 0)) {
                    el.value = u;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                } else if (el.type === 'password' || n.indexOf('pass') >= 0) {
                    el.value = p;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
            }
        })();
        """.trimIndent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("free_ssh_fetcher_dialog"),
        contentAlignment = Alignment.Center
    ) {
        M3SectionCard(
            cornerRadius = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
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
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Free SSH / TLS / UDP Fetcher",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Live 1–7 day accounts • Auto-rotates when expired",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_free_ssh_fetcher_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close SSH fetcher"
                        )
                    }
                }

                if (activeProvider == null) {
                    // 1. Direct SSH API / Subscription Feed Auto-Fetch
                    OutlinedTextField(
                        value = feedUrl,
                        onValueChange = { feedUrl = it },
                        label = { Text("SSH API / ssh:// Subscription Feed (Optional)") },
                        placeholder = { Text("https://your-panel.example/api/ssh") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ssh_feed_url_input")
                    )

                    Button(
                        onClick = { onAutoFetchFeed(feedUrl.trim()) },
                        enabled = !isFetching,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("auto_fetch_ssh_now_button")
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning Live SSH Endpoints…")
                        } else {
                            Text(
                                text = "Auto-Fetch Over Internet Now",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (captchaGatedProviders.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmberHandshaking.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AmberHandshaking.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${captchaGatedProviders.joinToString(", ")} require CAPTCHA/Cloudflare. Tap any provider below to auto-fill & grab a free 1–7d SSH account in 1 tap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // 2. 1-Tap Official Free SSH Providers (In-App WebView Auto-Grabber)
                    Text(
                        text = "1-Tap Live Free SSH Providers (TLS 443 + UDP 7300)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EphemeralAccountProvisioner.OFFICIAL_PROVIDERS.forEach { preset ->
                            Surface(
                                onClick = {
                                    activeProvider = preset
                                    webPageTitle = preset.name
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("provider_chip_${preset.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${preset.name} (${preset.defaultValidityDays}d)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Tap to Auto-Grab",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // 3. Direct Multi-Format Paste & Connect
                    OutlinedTextField(
                        value = pastePayload,
                        onValueChange = { pastePayload = it },
                        label = { Text("Or Paste ssh:// / JSON / Provider Confirmation Text") },
                        placeholder = { Text("ssh://user:pass@sg1.sshocean.net:443#SG") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paste_ssh_payload_input")
                    )

                    Button(
                        onClick = { onImportCapturedPayload(pastePayload, "Imported SSH") },
                        enabled = pastePayload.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("import_pasted_ssh_button")
                    ) {
                        Text(
                            text = "Verify & Import SSH Account",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    val currentPreset = activeProvider!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${currentPreset.name}: ${webPageTitle.ifBlank { currentPreset.createHint }}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                activeProvider = null
                                activeWebView = null
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Providers", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        webPageTitle = view?.title.orEmpty()
                                        view?.evaluateJavascript(autoFillJs, null)
                                    }
                                }
                                loadUrl(currentPreset.browseUrl)
                                activeWebView = this
                            }
                        },
                        update = { view ->
                            activeWebView = view
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                activeWebView?.evaluateJavascript(autoFillJs, null)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("autofill_webview_ssh_button")
                        ) {
                            Text(
                                text = "Auto-Fill",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Button(
                            onClick = {
                                val wv = activeWebView
                                if (wv != null) {
                                    wv.evaluateJavascript("(document.body ? document.body.innerText : '')") { rawJsResult ->
                                        val decoded = runCatching {
                                            JSONObject("{\"v\":$rawJsResult}").optString("v")
                                        }.getOrDefault(rawJsResult.orEmpty())
                                        onImportCapturedPayload(decoded, currentPreset.name)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.6f)
                                .height(44.dp)
                                .testTag("grab_webview_ssh_button")
                        ) {
                            Text(
                                text = "Grab SSH & Connect",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
