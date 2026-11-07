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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TunnelConnectionStatus
import com.example.model.VpnNode
import com.example.model.VpnProtocol
import com.example.ui.AetherTab
import com.example.ui.ConnectScreen
import com.example.ui.NodeListScreen
import com.example.ui.OpenSourceAuditScreen
import com.example.ui.VpnUiState
import com.example.ui.VpnViewModel
import com.example.ui.theme.AetherNodeTheme
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.vpn.AetherVpnStateBus
import kotlinx.coroutines.delay

/**
 * MainActivity for AetherNode — Lightweight Open-Source Worldwide VPN Client.
 */
class MainActivity : ComponentActivity() {

    private val vpnViewModel: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by vpnViewModel.uiState.collectAsStateWithLifecycle()

            AetherNodeTheme(darkTheme = uiState.preferences.isDarkTheme) {
                AetherNodeApp(
                    uiState = uiState,
                    viewModel = vpnViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherNodeApp(
    uiState: VpnUiState,
    viewModel: VpnViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var pendingNodeToConnect by remember { mutableStateOf<VpnNode?>(null) }

    // Back navigation handler for secondary tabs
    BackHandler(enabled = uiState.activeTab != AetherTab.CONNECT) {
        viewModel.selectTab(AetherTab.CONNECT)
    }

    // Optional Android 13+ notification permission launcher so foreground VPN status is visible
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with VPN connection regardless of notification permission result
        val targetNode = pendingNodeToConnect ?: uiState.selectedNode
        viewModel.startVpnServiceWithNode(context, targetNode)
        pendingNodeToConnect = null
    }

    // System Android VpnService permission dialog launcher
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
                viewModel.startVpnServiceWithNode(context, targetNode)
                pendingNodeToConnect = null
            }
        } else {
            AetherVpnStateBus.appendLog(
                tag = "OS",
                message = "VPN consent dialog cancelled; starting user-space encrypted tunnel preview."
            )
            // Start service in user-space preview mode if emulator doesn't show system VPN dialog
            viewModel.startVpnServiceWithNode(context, targetNode)
            pendingNodeToConnect = null
        }
    }

    val initiateVpnConnect: (VpnNode) -> Unit = { nodeToConnect ->
        pendingNodeToConnect = nodeToConnect
        val prepareIntent = try {
            VpnService.prepare(context)
        } catch (_: Exception) {
            null
        }
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnServiceWithNode(context, nodeToConnect)
            pendingNodeToConnect = null
        }
    }

    // Auto-dismiss transient banner feedback after 3.5 seconds
    LaunchedEffect(uiState.bannerFeedback) {
        if (uiState.bannerFeedback != null) {
            delay(3500L)
            viewModel.dismissBannerFeedback()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
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
                                    AetherTab.AUDIT -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge
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
                                initiateVpnConnect(uiState.selectedNode)
                            }
                        },
                        onConnectSmartFastest = {
                            val fastest = viewModel.selectFastestNodeAndGet()
                            initiateVpnConnect(fastest)
                        },
                        onOpenNodeSelector = {
                            viewModel.selectTab(AetherTab.NODES)
                        },
                        onPingSelectedNode = { node ->
                            viewModel.pingSingleNode(node)
                        },
                        onSelectProtocol = { proto ->
                            viewModel.updatePreferredProtocol(proto)
                        },
                        onInspectConfig = { node ->
                            viewModel.inspectNodeConfig(node)
                        },
                        onVerifyExitIp = {
                            viewModel.verifyCurrentExitIp()
                        }
                    )
                }

                AetherTab.NODES -> {
                    NodeListScreen(
                        uiState = uiState,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectRegion = viewModel::selectRegion,
                        onSelectSortOption = viewModel::selectSortOption,
                        onSelectAndConnectNode = { node ->
                            viewModel.selectNode(node)
                            viewModel.selectTab(AetherTab.CONNECT)
                            initiateVpnConnect(node.copy(protocol = uiState.preferences.preferredProtocol))
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onPingSingleNode = viewModel::pingSingleNode,
                        onPingAllNodes = viewModel::pingAllNodes,
                        onSyncPublicApi = { viewModel.syncLivePublicNodes(silent = false) },
                        onOpenAddCustomModal = { viewModel.setShowAddCustomNodeModal(true) },
                        onInspectNodeConfig = { node -> viewModel.inspectNodeConfig(node) }
                    )
                }

                AetherTab.AUDIT -> {
                    OpenSourceAuditScreen(
                        uiState = uiState,
                        onSelectDns = viewModel::updateDnsServer,
                        onToggleSafeSplitTunnel = viewModel::updateSafeSplitTunnel,
                        onToggleAutoReconnect = viewModel::updateAutoReconnect,
                        onClearLogs = viewModel::clearAuditLogs,
                        onShowCopiedFeedback = {
                            AetherVpnStateBus.appendLog("AUDIT", "Copied audit logs to clipboard.")
                        }
                    )
                }
            }

            // Floating Banner Feedback Pill
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
                    color = MaterialTheme.colorScheme.inverseSurface,
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

    // Modal 1: Inspect Node OpenVPN / Cryptographic Configuration Dialog
    uiState.inspectedNode?.let { inspected ->
        AlertDialog(
            onDismissRequest = { viewModel.inspectNodeConfig(null) },
            title = {
                Text(
                    text = "${inspected.flagEmoji} ${inspected.cityName} Relay Config",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Host: ${inspected.hostName}\nEndpoint: ${inspected.ipAddress}:${inspected.port}\nOperator: ${inspected.operatorName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = inspected.openVpnConfigPreview.ifEmpty {
                                "client\ndev tun\nremote ${inspected.ipAddress} ${inspected.port}\ncipher AES-256-GCM"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inspected.openVpnConfigPreview))
                        viewModel.inspectNodeConfig(null)
                    },
                    modifier = Modifier.testTag("copy_ovpn_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy .ovpn")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.inspectNodeConfig(null) },
                    modifier = Modifier.testTag("close_inspect_dialog_button")
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Modal 2: Add Custom Free Community Relay Node Dialog
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
    var operatorName by remember { mutableStateOf("Community Open Relay") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Free Community Relay Node",
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
                    label = { Text("Operator / Maintainer") },
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
                Text("Add & Select")
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
