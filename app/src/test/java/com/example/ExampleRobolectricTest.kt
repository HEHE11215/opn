package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.model.RelayTunnelType
import com.example.model.TunnelConnectionStatus
import com.example.model.TunnelTelemetry
import com.example.model.VpnDiagnosticEntry
import com.example.model.VpnDiagnosticSeverity
import com.example.model.VpnNode
import com.example.model.VpnRegion
import com.example.ui.BatteryOptimizationSettingCard
import com.example.ui.ConnectionLogScreen
import com.example.ui.FireConnectOrb
import com.example.ui.VpnDiagnosticPanel
import com.example.ui.VpnServiceStateIndicatorBar
import com.example.ui.theme.AetherNodeTheme
import com.example.vpn.AetherVpnService
import com.example.vpn.AetherVpnStateBus
import com.example.vpn.BackgroundLatencyWorkScheduler
import com.example.vpn.LocalTunnelGateway
import com.example.vpn.ServerLatencyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetBus() {
        AetherVpnStateBus.clearDiagnostics()
        AetherVpnStateBus.updateTelemetry { TunnelTelemetry() }
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("A VPN", appName)
    }

    @Test
    fun `diagnostic state bus captures and clears handshake failures`() {
        AetherVpnStateBus.recordDiagnostic(
            phase = "TLS Handshake Failed",
            serverLabel = "🇩🇪 Germany (203.0.113.10:1080)",
            summary = "TLS Handshake Failed",
            detail = "SSLHandshakeException: Handshake failed via relay",
            severity = VpnDiagnosticSeverity.ERROR
        )

        val telemetry = AetherVpnStateBus.telemetry.value
        assertEquals("TLS Handshake Failed", telemetry.lastFailureStage)
        assertTrue(telemetry.lastFailureReason?.contains("SSLHandshakeException") == true)
        assertEquals("🇩🇪 Germany (203.0.113.10:1080)", telemetry.lastFailedServerLabel)
        assertEquals(1, telemetry.diagnosticLogs.size)

        AetherVpnStateBus.clearDiagnostics()
        val cleared = AetherVpnStateBus.telemetry.value
        assertTrue(cleared.diagnosticLogs.isEmpty())
        assertNull(cleared.lastFailureReason)
    }

    @Test
    fun `verifyNodeEndToEndTlsDetailed returns structured stage for invalid node`() {
        val invalidNode = VpnNode(
            id = "invalid-1",
            hostName = "",
            ipAddress = "",
            port = 0,
            countryName = "Germany",
            countryCode = "DE",
            cityName = "Frankfurt",
            flagEmoji = "🇩🇪",
            region = VpnRegion.EUROPE,
            pingMs = -1,
            downloadSpeedMbps = 0.0,
            operatorName = "Test",
            relayType = RelayTunnelType.SOCKS5
        )
        val outcome = LocalTunnelGateway.verifyNodeEndToEndTlsDetailed(
            node = invalidNode,
            deviceDirectIp = null,
            timeoutMs = 500
        )
        assertNull(outcome.result)
        assertEquals("Invalid Endpoint", outcome.failureStage)
        assertNotNull(outcome.failureDetail)
    }

    @Test
    fun `visual indicator reflects VpnService connecting connected and disconnected states`() {
        val sampleNode = VpnNode(
            id = "country-DE",
            hostName = "198.51.100.2",
            ipAddress = "198.51.100.2",
            port = 1080,
            countryName = "Germany",
            countryCode = "DE",
            cityName = "Frankfurt",
            flagEmoji = "🇩🇪",
            region = VpnRegion.EUROPE,
            pingMs = 68,
            downloadSpeedMbps = 120.0,
            operatorName = "DE Relay"
        )

        val telemetryState = androidx.compose.runtime.mutableStateOf(
            TunnelTelemetry(status = TunnelConnectionStatus.DISCONNECTED)
        )
        composeRule.setContent {
            AetherNodeTheme(darkTheme = false) {
                VpnServiceStateIndicatorBar(
                    telemetry = telemetryState.value,
                    isDark = false
                )
            }
        }

        composeRule.onNodeWithTag("vpn_service_state_label", useUnmergedTree = true)
            .assertTextEquals("DISCONNECTED")

        composeRule.runOnUiThread {
            telemetryState.value = TunnelTelemetry(
                status = TunnelConnectionStatus.CONNECTING,
                activeNode = sampleNode,
                handshakeSummary = "Connecting Germany…"
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_service_state_label", useUnmergedTree = true)
            .assertTextEquals("CONNECTING")

        composeRule.runOnUiThread {
            telemetryState.value = TunnelTelemetry(
                status = TunnelConnectionStatus.CONNECTED,
                activeNode = sampleNode,
                verifiedExitCountry = "Germany",
                verifiedExitIp = "198.51.100.2"
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_service_state_label", useUnmergedTree = true)
            .assertTextEquals("CONNECTED")
    }

    @Test
    fun `diagnostic panel renders specific VpnService handshake error and log entry`() {
        val errorTelemetry = TunnelTelemetry(
            status = TunnelConnectionStatus.ERROR,
            errorMessage = "🇩🇪 Germany (198.51.100.5:1080): SOCKS5 TLS Handshake Failed — Relay rejected CONNECT tunnel",
            lastFailureStage = "SOCKS5 TLS Handshake Failed",
            lastFailureReason = "SOCKS5 TLS Handshake Failed: Relay rejected CONNECT tunnel",
            lastFailedServerLabel = "🇩🇪 Germany (198.51.100.5:1080)",
            diagnosticLogs = listOf(
                VpnDiagnosticEntry(
                    phase = "SOCKS5 TLS Handshake Failed",
                    serverLabel = "🇩🇪 Germany (198.51.100.5:1080)",
                    summary = "SOCKS5 TLS Handshake Failed",
                    detail = "Relay rejected CONNECT tunnel to api.ipify.org:443",
                    severity = VpnDiagnosticSeverity.ERROR
                )
            )
        )

        composeRule.setContent {
            AetherNodeTheme(darkTheme = true) {
                VpnDiagnosticPanel(
                    telemetry = errorTelemetry,
                    isDark = true,
                    onClearDiagnostics = {},
                    onRetryFastSwitch = {},
                    onBrowseServers = {}
                )
            }
        }

        composeRule.onNodeWithTag("vpn_diagnostic_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("vpn_diagnostic_failure_callout").assertIsDisplayed()
        composeRule.onAllNodesWithText("SOCKS5 TLS Handshake Failed", substring = true)
            .onFirst()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Relay rejected CONNECT tunnel to api.ipify.org:443", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `foreground service creates notification channel and handles null START_STICKY recovery safely`() {
        val controller = Robolectric.buildService(AetherVpnService::class.java).create()
        val service = controller.get()
        val shadowService = Shadows.shadowOf(service)

        val nm = service.getSystemService(NotificationManager::class.java)
        assertNotNull(nm?.getNotificationChannel("avpn_tunnel_channel"))

        // Simulate START_STICKY recovery with null intent when no prior session exists:
        // enters foreground immediately, then cleanly stops foreground & self
        service.onStartCommand(null, 0, 1)
        assertTrue(shadowService.isForegroundStopped)
        assertTrue(shadowService.isStoppedBySelf)
        assertFalse(AetherVpnStateBus.telemetry.value.isForegroundServiceActive)
        assertEquals(TunnelConnectionStatus.DISCONNECTED, AetherVpnStateBus.telemetry.value.status)

        // Simulate ACTION_DISCONNECT dispatched via startForegroundService
        val disconnectIntent = Intent(service, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_DISCONNECT
        }
        service.onStartCommand(disconnectIntent, 0, 2)
        assertTrue(shadowService.isForegroundStopped)
        assertEquals(TunnelConnectionStatus.DISCONNECTED, AetherVpnStateBus.telemetry.value.status)

        controller.destroy()
    }

    @Test
    fun `visual indicator shows FGS protection badge when foreground service is active`() {
        composeRule.setContent {
            AetherNodeTheme(darkTheme = false) {
                VpnServiceStateIndicatorBar(
                    telemetry = TunnelTelemetry(
                        status = TunnelConnectionStatus.CONNECTED,
                        isForegroundServiceActive = true,
                        verifiedExitCountry = "United States",
                        verifiedExitIp = "198.51.100.77"
                    ),
                    isDark = false
                )
            }
        }

        composeRule.onNodeWithText("FGS").assertIsDisplayed()
        composeRule.onNodeWithText("TLS").assertIsDisplayed()
        composeRule.onNodeWithText("TUN0").assertIsDisplayed()
    }

    @Test
    fun `connection log screen renders verbose handshake status and specific server failures`() {
        val telemetry = TunnelTelemetry(
            status = TunnelConnectionStatus.ERROR,
            handshakeSummary = "TCP SYN timed out",
            errorMessage = "🇩🇪 Germany (93.184.7.217:1080): TCP Timeout — TCP SYN timed out after 4500ms",
            lastFailureStage = "TCP Timeout",
            lastFailureReason = "TCP SYN timed out after 4500ms reaching 93.184.7.217:1080.",
            lastFailedServerLabel = "🇩🇪 Germany (93.184.7.217:1080)",
            diagnosticLogs = listOf(
                VpnDiagnosticEntry(
                    phase = "Connecting",
                    serverLabel = "🇩🇪 Germany (93.184.7.217:1080)",
                    summary = "Initiating TLS & Tunnel Probe",
                    detail = "Testing SOCKS5 TLS relay via protected socket",
                    severity = VpnDiagnosticSeverity.INFO
                ),
                VpnDiagnosticEntry(
                    phase = "TCP Timeout",
                    serverLabel = "🇩🇪 Germany (93.184.7.217:1080)",
                    summary = "TCP SYN timed out",
                    detail = "TCP SYN timed out after 4500ms reaching 93.184.7.217:1080.",
                    severity = VpnDiagnosticSeverity.ERROR
                )
            )
        )

        composeRule.setContent {
            AetherNodeTheme(darkTheme = false) {
                ConnectionLogScreen(
                    telemetry = telemetry,
                    isDark = false,
                    onClearLogs = {},
                    onRetryFastSwitch = {},
                    onBrowseServers = {}
                )
            }
        }

        composeRule.onNodeWithTag("connection_log_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("connection_log_status_header").assertIsDisplayed()
        composeRule.onNodeWithTag("connection_log_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("VpnService Connection Log").assertIsDisplayed()
        composeRule.onAllNodesWithText("TCP Timeout", substring = true).onFirst().assertIsDisplayed()

        // Scroll to filter bar, select Errors only, and inspect verbose log detail from AetherVpnService
        composeRule.onNodeWithTag("connection_log_screen")
            .performScrollToNode(hasTestTag("log_filter_errors"))
        composeRule.onNodeWithTag("log_filter_errors").performClick()
        composeRule.onNodeWithTag("connection_log_screen")
            .performScrollToNode(hasTestTag("connection_log_entry_card"))
        composeRule.onAllNodesWithText("TCP SYN timed out after 4500ms reaching 93.184.7.217:1080.", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun `latency worker updates measured TCP handshake durations and evicts dead public servers`() = runBlocking {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val worker = ServerLatencyWorker(scope = workerScope, intervalMs = 15_000L, maxConcurrentTests = 2)

        val liveNode = VpnNode(
            id = "country-US",
            hostName = "198.51.100.1",
            ipAddress = "198.51.100.1",
            port = 1080,
            countryName = "United States",
            countryCode = "US",
            cityName = "Ashburn",
            flagEmoji = "🇺🇸",
            region = VpnRegion.AMERICAS,
            pingMs = 120,
            downloadSpeedMbps = 100.0,
            operatorName = "Test"
        )
        val deadNode = VpnNode(
            id = "country-DE",
            hostName = "198.51.100.2",
            ipAddress = "198.51.100.2",
            port = 1080,
            countryName = "Germany",
            countryCode = "DE",
            cityName = "Frankfurt",
            flagEmoji = "🇩🇪",
            region = VpnRegion.EUROPE,
            pingMs = 95,
            downloadSpeedMbps = 100.0,
            operatorName = "Test"
        )

        val fakeProbe: (String, Int, Int) -> Int? = { ip, _, _ ->
            if (ip == "198.51.100.1") 42 else null
        }

        // Pass 1: dead node kept once with 1 miss, live node updated to measured 42ms TCP handshake duration
        val pass1 = worker.measureTcpHandshakesOnce(listOf(liveNode, deadNode), probeFn = fakeProbe)
        assertEquals(2, pass1.size)
        assertEquals(42, pass1.first { it.countryCode == "US" }.pingMs)

        // Pass 2: dead public node misses 2nd consecutive TCP handshake and is evicted cleanly
        val pass2 = worker.measureTcpHandshakesOnce(pass1, probeFn = fakeProbe)
        assertEquals(1, pass2.size)
        assertEquals("US", pass2.first().countryCode)
        assertEquals(42, pass2.first().pingMs)
    }

    @Test
    fun `work manager scheduler enforces unmetered wifi constraint when battery optimization enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)

        val wifiConstraints = BackgroundLatencyWorkScheduler.buildConstraints(wifiOnly = true)
        assertEquals(NetworkType.UNMETERED, wifiConstraints.requiredNetworkType)
        assertTrue(wifiConstraints.requiresBatteryNotLow())

        val allNetConstraints = BackgroundLatencyWorkScheduler.buildConstraints(wifiOnly = false)
        assertEquals(NetworkType.CONNECTED, allNetConstraints.requiredNetworkType)
        assertFalse(allNetConstraints.requiresBatteryNotLow())

        BackgroundLatencyWorkScheduler.scheduleOrUpdate(context, wifiOnly = true)
        val workInfos = workManager.getWorkInfosForUniqueWork(BackgroundLatencyWorkScheduler.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
        val info = workInfos.first()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
        assertTrue(info.tags.contains(BackgroundLatencyWorkScheduler.TAG_WIFI_ONLY))
    }

    @Test
    fun `battery optimization card toggles wifi only switch and connecting orb shows pulse only while connecting`() {
        val wifiOnlyState = androidx.compose.runtime.mutableStateOf(false)
        val statusState = androidx.compose.runtime.mutableStateOf(TunnelConnectionStatus.DISCONNECTED)

        composeRule.setContent {
            AetherNodeTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    FireConnectOrb(
                        status = statusState.value,
                        isDark = false,
                        onClick = {}
                    )
                    BatteryOptimizationSettingCard(
                        wifiOnly = wifiOnlyState.value,
                        isDark = false,
                        onToggle = { wifiOnlyState.value = it }
                    )
                }
            }
        }

        // Disconnected state: pulse ring must not exist (0 idle frame animations)
        composeRule.onNodeWithTag("connecting_pulse_ring").assertDoesNotExist()
        composeRule.onNodeWithText("All Nets").assertIsDisplayed()

        // Switch to CONNECTING state: subtle pulse ring mounts
        composeRule.runOnUiThread {
            statusState.value = TunnelConnectionStatus.CONNECTING
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connecting_pulse_ring").assertExists()

        // Switch to CONNECTED state: pulse ring unmounts again
        composeRule.runOnUiThread {
            statusState.value = TunnelConnectionStatus.CONNECTED
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connecting_pulse_ring").assertDoesNotExist()

        // Toggle Wi-Fi Only battery optimization switch
        composeRule.onNodeWithTag("wifi_only_latency_switch").performClick()
        composeRule.waitForIdle()
        assertTrue(wifiOnlyState.value)
        composeRule.onNodeWithText("Wi-Fi Only").assertIsDisplayed()
    }
}
