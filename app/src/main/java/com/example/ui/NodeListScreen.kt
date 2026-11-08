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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.ui.theme.SkyBluePrimary
import java.util.Locale

private val FireAccent = Color(0xFFFF5E1A)

/**
 * Lightweight iPhone Glassmorphic Locations Screen:
 * - Strictly ONE card per country (fastest server automatically chosen for each country).
 * - Tapping any country locks onto that exact country and connects immediately!
 */
@Composable
fun NodeListScreen(
    uiState: VpnUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectRegion: (VpnRegion) -> Unit,
    onSelectSortOption: (NodeSortOption) -> Unit,
    onSelectAutoFastest: () -> Unit,
    onSelectAndConnectNode: (VpnNode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSyncPublicApi: () -> Unit,
    onOpenAddCustomModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = uiState.preferences.isDarkTheme

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

            // 1. iPhone Glass Search Bar + Refresh & Add Server Buttons
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
                            text = "Search country…",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search country"
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
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        focusedBorderColor = SkyBluePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                // Auto-Refresh Live Servers Button
                IosGlassCard(
                    isDark = isDark,
                    cornerRadius = 16.dp,
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(
                        onClick = onSyncPublicApi,
                        enabled = !uiState.isSyncingApi,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("refresh_nodes_button")
                    ) {
                        if (uiState.isSyncingApi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh fastest servers",
                                tint = SkyBluePrimary
                            )
                        }
                    }
                }

                // Add Custom Server Button
                IosGlassCard(
                    isDark = isDark,
                    cornerRadius = 16.dp,
                    accentGlowColor = SkyBluePrimary,
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(
                        onClick = onOpenAddCustomModal,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("add_custom_node_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom server",
                            tint = SkyBluePrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SkyBluePrimary.copy(alpha = 0.18f),
                            selectedLabelColor = SkyBluePrimary
                        ),
                        modifier = Modifier.testTag("region_chip_${region.name.lowercase(Locale.US)}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Clean Single-Line Sort Order Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sort:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                NodeSortOption.entries.forEach { sortOpt ->
                    val active = uiState.sortOption == sortOpt
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) {
                            SkyBluePrimary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = if (active) {
                            BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.5f))
                        } else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectSortOption(sortOpt) }
                            .testTag("sort_chip_${sortOpt.name.lowercase(Locale.US)}")
                    ) {
                        Text(
                            text = sortOpt.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (active) {
                                SkyBluePrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. Country List (Featured Auto-Fastest Card + 1 Card Per Country)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("worldwide_node_lazy_list"),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "auto_fastest_header_card") {
                AutoFastestLocationCard(
                    isSelected = uiState.isAutoFastestMode,
                    fastestNode = uiState.filteredNodes.firstOrNull() ?: uiState.selectedNode,
                    isDark = isDark,
                    onClick = onSelectAutoFastest
                )
            }

            if (uiState.filteredNodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No matching countries found",
                                style = MaterialTheme.typography.titleMedium
                            )
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
                        isDark = isDark,
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
    fastestNode: VpnNode,
    isDark: Boolean,
    onClick: () -> Unit
) {
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
        accentGlowColor = if (isSelected) SkyBluePrimary else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("auto_fastest_location_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SkyBluePrimary.copy(alpha = 0.16f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto Fastest Server",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Best speed (${fastestNode.flagEmoji} ${fastestNode.countryName} • ${fastestNode.pingMs} ms)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldConnected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) EmeraldConnected else SkyBluePrimary
                ),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (isSelected) "Active" else "Auto",
                    style = MaterialTheme.typography.labelLarge.copy(color = Color.White),
                    maxLines = 1
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
    isDark: Boolean,
    onSelectNode: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    IosGlassCard(
        isDark = isDark,
        cornerRadius = 20.dp,
        accentGlowColor = if (isSelected) FireAccent else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectNode)
            .testTag("node_card_${node.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Country Flag Box
            Surface(
                shape = RoundedCornerShape(12.dp),
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

            // 2. Country Name + Speed & Ping Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.countryName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalStrengthIndicator(
                        bars = node.signalBars,
                        color = latencyColor(node.pingMs)
                    )
                    Text(
                        text = "${node.pingMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = latencyColor(node.pingMs),
                        maxLines = 1
                    )
                    Text(
                        text = "• ${node.cityName}",
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
                    .size(40.dp)
                    .testTag("fav_button_${node.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Unstar country" else "Star country",
                    tint = if (isFavorite) AmberHandshaking else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 4. Clean Connect Button that locks onto this exact country
            Button(
                onClick = onSelectNode,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) FireAccent else SkyBluePrimary
                ),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("select_node_${node.id}")
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Locked",
                        style = MaterialTheme.typography.labelLarge.copy(color = Color.White),
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.labelLarge.copy(color = Color.White),
                        maxLines = 1
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
        modifier = Modifier.height(13.dp)
    ) {
        for (i in 1..4) {
            val active = i <= bars
            val barHeight = (4 + (i * 2.2)).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(if (active) color else color.copy(alpha = 0.22f))
            )
        }
    }
}
