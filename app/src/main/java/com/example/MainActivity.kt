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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnNode
import com.example.ui.AetherTab
import com.example.ui.ConnectScreen
import com.example.ui.NodeListScreen
import com.example.ui.VpnUiState
import com.example.ui.VpnViewModel
import com.example.ui.theme.AetherNodeTheme
import com.example.ui.theme.SkyBluePrimary
import com.example.vpn.AetherVpnStateBus
import kotlinx.coroutines.delay

/**
 * MainActivity for **A VPN** — Fast Worldwide Free VPN with iPhone Glassmorphic UI,
 * Animated Fire Connect Orb, Manual Country Lock, and Startup Splash Screen.
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
    var pendingNodeToConnect by remember { mutableStateOf<VpnNode?>(null) }
    var pendingIsManualSelection by remember { mutableStateOf(false) }

    // Dismiss startup splash after 1.65 seconds
    LaunchedEffect(Unit) {
        delay(1650L)
        showStartupSplash = false
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

    // Connects to the exact target node and explicitly preserves manual vs auto mode!
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

    // Auto-dismiss transient banner feedback after 3 seconds
    LaunchedEffect(uiState.bannerFeedback) {
        if (uiState.bannerFeedback != null) {
            delay(3000L)
            viewModel.dismissBannerFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Lightweight iPhone Glass Mesh Gradient Backdrop
        IosMeshBackdrop(isDark = uiState.preferences.isDarkTheme)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_avpn_logo_1794127481273),
                                contentDescription = "A VPN Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
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
                                    text = stringResource(R.string.tagline_open_source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkTheme(!uiState.preferences.isDarkTheme) },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.preferences.isDarkTheme) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                                contentDescription = "Toggle light or dark theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                // Clean 2-Tab iPhone Glass Bottom Navigation Bar (Connect & Servers only)
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = if (uiState.preferences.isDarkTheme) 0.78f else 0.85f
                    ),
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
                                val fastest = viewModel.enableAutoFastestModeAndGetNode()
                                initiateVpnConnect(fastest, false)
                            },
                            onOpenNodeSelector = {
                                viewModel.selectTab(AetherTab.NODES)
                            }
                        )
                    }

                    AetherTab.NODES -> {
                        NodeListScreen(
                            uiState = uiState,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSelectRegion = viewModel::selectRegion,
                            onSelectSortOption = viewModel::selectSortOption,
                            onSelectAutoFastest = {
                                val fastest = viewModel.enableAutoFastestModeAndGetNode()
                                initiateVpnConnect(fastest, false)
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
                }

                // Floating Glass Banner Feedback Pill
                AnimatedVisibility(
                    visible = uiState.bannerFeedback != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = uiState.bannerFeedback.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Animated Startup Splash Screen with "A" VPN Logo & "Made with love by Ashir ♥️"
        AnimatedVisibility(
            visible = showStartupSplash,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(450))
        ) {
            AVpnSplashScreen(
                onDismiss = { showStartupSplash = false }
            )
        }
    }

    // Add Custom Server Modal
    if (uiState.showAddCustomNodeModal) {
        AddCustomNodeDialog(
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
}

/**
 * Ultra-Lightweight iOS Glassmorphic Mesh Gradient Background.
 */
@Composable
private fun IosMeshBackdrop(isDark: Boolean) {
    val bgColor = if (isDark) Color(0xFF080E1A) else Color(0xFFEAF2FF)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Top-left cyan/blue glass orb glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SkyBluePrimary.copy(alpha = if (isDark) 0.22f else 0.25f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, size.height * 0.14f),
                radius = size.minDimension * 0.72f
            ),
            radius = size.minDimension * 0.72f,
            center = Offset(size.width * 0.18f, size.height * 0.14f)
        )

        // Center-right warm fire glass orb glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF5E1A).copy(alpha = if (isDark) 0.16f else 0.15f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.42f),
                radius = size.minDimension * 0.68f
            ),
            radius = size.minDimension * 0.68f,
            center = Offset(size.width * 0.85f, size.height * 0.42f)
        )
    }
}

/**
 * Startup Splash Screen featuring the "A" VPN Logo, Fire Glow, and "Made with love by Ashir ♥️" at the bottom.
 */
@Composable
private fun AVpnSplashScreen(
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_glow")
    val logoPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_logo_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070D19),
                        Color(0xFF0E1A32),
                        Color(0xFF080E1A)
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(144.dp)
                    .scale(logoPulse)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF5E1A).copy(alpha = 0.22f),
                    modifier = Modifier.size(144.dp)
                ) {}

                Image(
                    painter = painterResource(id = R.drawable.img_avpn_logo_1794127481273),
                    contentDescription = "A VPN Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFF5E1A),
                    modifier = Modifier.size(28.dp)
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
                text = "Ultra-Fast Worldwide Glass VPN",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f)
            )
        }

        // Bottom Credit Pill: "Made with love by Ashir ♥️"
        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .testTag("splash_made_by_ashir_badge")
        ) {
            Text(
                text = stringResource(R.string.made_by_ashir),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                ),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun AddCustomNodeDialog(
    onDismiss: () -> Unit,
    onConfirmAdd: (country: String, code: String, city: String, ip: String, port: Int, operator: String) -> Unit
) {
    var countryName by remember { mutableStateOf("Finland") }
    var countryCode by remember { mutableStateOf("FI") }
    var cityName by remember { mutableStateOf("Helsinki") }
    var ipAddress by remember { mutableStateOf("95.216.144.18") }
    var portText by remember { mutableStateOf("443") }
    var operatorName by remember { mutableStateOf("Custom Server") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Server",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPort = portText.toIntOrNull() ?: 443
                    onConfirmAdd(countryName, countryCode, cityName, ipAddress, parsedPort, operatorName)
                },
                modifier = Modifier.testTag("confirm_add_custom_node_button")
            ) {
                Text("Add & Connect")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_custom_node_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
