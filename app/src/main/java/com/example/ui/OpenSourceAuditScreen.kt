package com.example.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AuditLogEntry
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SkyBluePrimary

private val PUBLIC_DNS_PROVIDERS = listOf(
    "1.1.1.1" to "Cloudflare (1.1.1.1)",
    "9.9.9.9" to "Quad9 Threat-Block (9.9.9.9)",
    "94.140.14.14" to "AdGuard DNS (94.140.14.14)",
    "208.67.222.222" to "OpenDNS (208.67.222.222)"
)

private val GITHUB_RELEASE_WORKFLOW_SNIPPET = """
name: Build & Publish Android APK Release
on:
  push:
    branches: [ main, master, "release/**" ]
    tags: [ "v*" ]
  workflow_dispatch:
permissions:
  contents: write
jobs:
  build-and-release-apk:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: "9.3.1" }
      - run: gradle :app:assembleRelease :app:assembleDebug :app:bundleRelease
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: v1.0.${'$'}{{ github.run_number }}
          body_path: RELEASE_NOTES.md
          files: |
            release-assets/AetherNode-*-release-signed.apk
            release-assets/AetherNode-*-debug.apk
            release-assets/SHA256SUMS.txt
""".trimIndent()

/**
 * Open-Source Transparency, Privacy Settings, GitHub CI/CD APK Release Flow &
 * Android 8–16 Compatibility Audit Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenSourceAuditScreen(
    uiState: VpnUiState,
    onSelectDns: (String) -> Unit,
    onToggleSafeSplitTunnel: (Boolean) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onShowCopiedFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showWorkflowPreview by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("opensource_audit_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Automated GitHub CI/CD APK Release Workflow Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .testTag("github_release_pipeline_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "GitHub APK Release Flow",
                                tint = SkyBluePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub Repo & Auto APK Release CI/CD",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EmeraldConnected.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "READY ON PUSH",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldConnected,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Configured in .github/workflows/android-apk-release.yml. Every push to your GitHub repository automatically compiles signed Release & Debug APKs, generates SHA-256/MD5 checksums and commit changelogs, and publishes a full GitHub Release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CompatibilityRow("Trigger", "Every push to main, master, release/**, or v* tags")
                    CompatibilityRow("Artifacts", "Signed Release APK + Debug APK + Release AAB + SHA256SUMS.txt")
                    CompatibilityRow("Release Notes", "Auto-generated commit changelog, APK sizes, SHA-256 hashes & SDK matrix")

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showWorkflowPreview = !showWorkflowPreview },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_workflow_yaml_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showWorkflowPreview) "Hide Workflow" else "Inspect Workflow")
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(GITHUB_RELEASE_WORKFLOW_SNIPPET))
                                onShowCopiedFeedback()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_workflow_yaml_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy YAML")
                        }
                    }

                    AnimatedVisibility(visible = showWorkflowPreview) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = GITHUB_RELEASE_WORKFLOW_SNIPPET,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Open-Source Architecture & Zero-Tracking Transparency Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Open-source verified",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open-Source Transparency & Zero-Log Core",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AetherNode is engineered for community auditability. All connections use Android's native VpnService API with zero telemetry trackers, zero third-party ad SDKs, and direct public relay endpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• Public Relay Directory: Academic VPN Gate API + Community Nodes",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "• Ciphers: ChaCha20-Poly1305 / AES-256-GCM (TLS 1.3 Control)",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "• License: Open-Source GPLv3 / Apache-2.0 Community Core",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // 3. Tunnel Privacy & DNS Resolver Configuration Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "DNS settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Encrypted DNS & Routing Controls",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PUBLIC_DNS_PROVIDERS.forEach { (ip, label) ->
                            val selected = uiState.preferences.dnsServer == ip
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDns(ip) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("dns_chip_$ip")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Safe Split-Tunnel Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Safe Split-Tunnel Routing (10.8.0.0/24)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Routes virtual tunnel traffic via tun0 while keeping local LAN sockets uninterrupted.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.preferences.safeSplitTunnelMode,
                            onCheckedChange = onToggleSafeSplitTunnel,
                            modifier = Modifier.testTag("split_tunnel_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Reconnect Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Heal Tunnel on Wi-Fi / 5G Handoff",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Automatically re-establishes the encrypted session if the underlying network changes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.preferences.autoReconnect,
                            onCheckedChange = onToggleAutoReconnect,
                            modifier = Modifier.testTag("auto_reconnect_switch")
                        )
                    }
                }
            }
        }

        // 4. Android 8.0 (Oreo) to Android 16 (API 36) Device Compatibility Matrix
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Android compatibility",
                            tint = SkyBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android 8.0 – Android 16+ Compatibility",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Detected Runtime: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldConnected
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CompatibilityRow("Android 8.0–9.0 (API 26–28)", "NotificationChannel + VpnService.Builder Ready")
                    CompatibilityRow("Android 10–13 (API 29–33)", "Non-Metered TUN + Scoped Storage Compliant")
                    CompatibilityRow("Android 14–16+ (API 34–36)", "ForegroundServiceType SpecialUse Verified")
                }
            }
        }

        // 5. Live Cryptographic & Socket Audit Log Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Live audit log",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Open-Source Tunnel Audit Log",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    val fullLog = uiState.auditLogs.joinToString("\n") {
                                        "[${it.timestampFormatted}] [${it.tag}] ${it.message}"
                                    }
                                    clipboardManager.setText(AnnotatedString(fullLog))
                                    onShowCopiedFeedback()
                                },
                                modifier = Modifier.testTag("copy_audit_log_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy audit log",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onClearLogs,
                                modifier = Modifier.testTag("clear_audit_log_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear audit log",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.auditLogs.take(25).forEach { entry ->
                            AuditLogItemRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatibilityRow(
    apiRange: String,
    featureNote: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = EmeraldConnected,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$apiRange: ",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = featureNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuditLogItemRow(entry: AuditLogEntry) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (entry.isHighlight) {
            EmeraldConnected.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "${entry.timestampFormatted} [${entry.tag}]",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = if (entry.isHighlight) EmeraldConnected else SkyBluePrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
