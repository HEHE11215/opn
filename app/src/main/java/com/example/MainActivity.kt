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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * MainActivity for **A VPN** — Crafted with love by **Ashir ♥️**.
 * Simple Material 3 UI with instant startup, zero glass overlays, zero unnecessary animations,
 * and seamless Day/Night theme switching.
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

    // Auto-dismiss event popup after 2.5 seconds
    LaunchedEffect(uiState.activePopup?.id) {
        if (uiState.activePopup != null) {
            delay(2500L)
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
                                cornerRadius = 10.dp
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
                        // Material 3 "Ashir ♥️" Developer Pill
                        Surface(
                            onClick = { viewModel.setShowDeveloperCreditsModal(true) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("topbar_ashir_credits_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Developer Credits",
                                    tint = RoseAlert,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ashir",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

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

        // Clean Material 3 Event Popup Dialog (direct conditional render, zero bounce/glass)
        uiState.activePopup?.let { popup ->
            MaterialEventPopupDialog(
                popup = popup,
                onDismiss = viewModel::dismissPopup
            )
        }

        // Clean Material 3 Developer Credits Dialog
        if (uiState.showDeveloperCreditsModal) {
            AshirDeveloperCreditsDialog(
                onDismiss = { viewModel.setShowDeveloperCreditsModal(false) }
            )
        }

        // Clean Material 3 Add Custom Server Dialog
        if (uiState.showAddCustomNodeModal) {
            AddCustomNodeDialog(
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
    }
}

/**
 * Simple Material 3 Day / Night Switch button (`MaterialDayNightToggle`).
 * Switches between Day (Light) and Night (Dark) instantaneously without sliding thumbs,
 * rotating icons, or radial Canvas waves.
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
                        cornerRadius = 16.dp
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

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = RoseAlert,
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
 * Clean Material 3 Developer Credits Dialog showcasing Ashir ♥️.
 */
@Composable
private fun AshirDeveloperCreditsDialog(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        M3SectionCard(
            cornerRadius = 20.dp,
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
                    size = 68.dp,
                    cornerRadius = 16.dp
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Founder & Lead Architect of A VPN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Crafted with love by Ashir ♥️ to deliver native Wi-Fi speed tunnels, Quick Settings 1-tap controls, and a clean Material 3 Day & Night UI.",
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
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
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Wi-Fi Warp",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
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
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "By Ashir ♥️",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
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
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Clean Material 3 Add Custom Server Dialog.
 */
@Composable
private fun AddCustomNodeDialog(
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
                                        fontSize = 11.sp
                                    ),
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
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
