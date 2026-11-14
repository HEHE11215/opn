package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NodeSortOption
import com.example.model.VpnNode
import com.example.model.VpnRegion
import com.example.ui.theme.AmberHandshaking
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

/**
 * Clean Material 3 Servers Screen (`NodeListScreen`) with standard Material 3 cards, chips,
 * and icon buttons. Zero glass overlays or idle animations.
 */
@Composable
fun NodeListScreen(
    uiState: VpnUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectRegion: (VpnRegion) -> Unit,
    onSelectSortOption: (NodeSortOption) -> Unit,
    onTogglePingSort: () -> Unit,
    onSelectAutoFastest: () -> Unit,
    onSelectAndConnectNode: (VpnNode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSyncPublicApi: () -> Unit,
    onOpenAddCustomModal: () -> Unit,
    onOpenDeveloperCredits: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = uiState.preferences.isDarkTheme
    val isPingAsc = uiState.sortOption == NodeSortOption.PING_ASC

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Material 3 Search Bar + Ping ↑/↓ + Sync + Add Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("node_search_input"),
                    placeholder = {
                        Text(
                            text = "Search…",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search country",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Compact 1-Tap Ping Asc ↑ / Desc ↓ Toggle Button
                OutlinedButton(
                    onClick = onTogglePingSort,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ping_asc_desc_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isPingAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = "Toggle Ping Sort Asc or Desc",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPingAsc) "Ping ↑" else "Ping ↓",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Auto-Refresh Live Tunnels Button
                FilledTonalIconButton(
                    onClick = onSyncPublicApi,
                    enabled = !uiState.isSyncingApi,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_nodes_button")
                ) {
                    if (uiState.isSyncingApi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Fetch latest servers",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Add Custom Server Button
                FilledTonalIconButton(
                    onClick = onOpenAddCustomModal,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("add_custom_node_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add custom server",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Single-Line Region Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VpnRegion.entries.forEach { region ->
                    val isSelected = uiState.selectedRegion == region
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectRegion(region) },
                        label = {
                            Text(
                                text = region.displayName,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("region_chip_${region.name.lowercase(Locale.US)}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Sort Bar: Ping: Asc ↑ | Ping: Desc ↓ | Speed ⚡ | Country A–Z
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sort:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                NodeSortOption.entries.forEach { sortOpt ->
                    val active = uiState.sortOption == sortOpt
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (active) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectSortOption(sortOpt) }
                            .testTag("sort_chip_${sortOpt.name.lowercase(Locale.US)}")
                    ) {
                        Text(
                            text = sortOpt.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. Country List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("worldwide_node_lazy_list"),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "auto_fastest_header_card") {
                val fastestOverall = uiState.allNodes.minByOrNull { it.pingMs } ?: uiState.selectedNode
                AutoFastestLocationCard(
                    isSelected = uiState.isAutoFastestMode,
                    isSyncing = uiState.isSyncingApi,
                    fastestNode = fastestOverall,
                    onClick = onSelectAutoFastest
                )
            }

            if (uiState.filteredNodes.isEmpty()) {
                item {
                    M3SectionCard(
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (uiState.isSyncingApi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Testing Live Relays (TCP + TLS Exit IP)…",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (uiState.allNodes.isEmpty()) {
                                        "No live public relays passed TLS verification"
                                    } else {
                                        "No matching countries"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onSyncPublicApi,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "Scan Relays",
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onOpenAddCustomModal,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "+ Custom",
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = uiState.filteredNodes,
                    key = { it.countryCode + "_" + it.id }
                ) { node ->
                    val isSelected = !uiState.isAutoFastestMode &&
                        (uiState.selectedNode.countryCode.equals(node.countryCode, ignoreCase = true))
                    val isFavorite = uiState.preferences.favoriteNodeIds.contains(node.countryCode) ||
                        uiState.preferences.favoriteNodeIds.contains(node.id)

                    CountryServerRowCard(
                        node = node,
                        isSelected = isSelected,
                        isFavorite = isFavorite,
                        onSelectNode = { onSelectAndConnectNode(node) },
                        onToggleFavorite = { onToggleFavorite(node.countryCode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoFastestLocationCard(
    isSelected: Boolean,
    isSyncing: Boolean,
    fastestNode: VpnNode,
    onClick: () -> Unit
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        borderColor = if (isSelected) EmeraldConnected else MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("auto_fastest_location_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto Fastest Verified",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val fastPingText = if (fastestNode.pingMs > 0) "${fastestNode.pingMs} ms" else "Scanning…"
                Text(
                    text = "${fastestNode.flagEmoji} ${fastestNode.countryName} • ⚡ $fastPingText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldConnected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) EmeraldConnected else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .height(38.dp)
            ) {
                Text(
                    text = if (isSelected) "Active" else "Auto ⚡",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CountryServerRowCard(
    node: VpnNode,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelectNode: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    M3SectionCard(
        cornerRadius = 16.dp,
        borderColor = if (isSelected) EmeraldConnected else MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectNode)
            .testTag("node_card_${node.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Country Flag Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = node.flagEmoji,
                        fontSize = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Country Name + Ping Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.countryName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalStrengthIndicator(
                        bars = node.signalBars,
                        color = latencyColor(node.pingMs)
                    )
                    Text(
                        text = if (node.pingMs > 0) "${node.pingMs} ms" else "Offline",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = latencyColor(node.pingMs),
                        maxLines = 1
                    )
                    val metaLabel = if (node.verifiedExitIp.isNotEmpty()) {
                        "• ${node.relayType.badgeLabel} • ${node.verifiedExitIp}"
                    } else {
                        "• ${node.cityName} • ${node.relayType.badgeLabel}"
                    }
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. Star Icon Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("fav_button_${node.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Unstar country" else "Star country",
                    tint = if (isFavorite) AmberHandshaking else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 4. Compact Button with balanced padding & single-line label
            Button(
                onClick = onSelectNode,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) EmeraldConnected else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("select_node_${node.id}")
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Use",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalStrengthIndicator(
    bars: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(12.dp)
    ) {
        for (i in 1..4) {
            val active = i <= bars
            val barHeight = (3.5 + (i * 2.0)).dp
            Box(
                modifier = Modifier
                    .width(2.8.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(if (active) color else color.copy(alpha = 0.22f))
            )
        }
    }
}
