package com.GXR806.envhealth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.GXR806.envhealth.data.local.datastore.SettingsSnapshot
import com.GXR806.envhealth.ui.component.*
import com.GXR806.envhealth.ui.screen.ThemeModeOptions
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Simulated intent/state harness, NOT permission-system, hardware, service or end-to-end evidence.
 * Renders the same production components used by Main/Home/Dashboard/Settings, no Application cast.
 */
class ProductionUiHarnessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun simulatedAuthorization_startRealtime_stopAndOpenReport() {
        var permitted by mutableStateOf(false)
        var monitoring by mutableStateOf(false)
        var pending by mutableStateOf(false)
        var report by mutableStateOf(false)
        var starts = 0
        var stops = 0
        var opens = 0
        compose.setContent { MaterialTheme { Column {
            MonitorControls(monitoring, true) {
                if (monitoring) { monitoring = false; stops++; report = true }
                else if (permitted) { starts++; monitoring = true } else pending = true
            }
            if (pending) com.GXR806.envhealth.util.permission.PermissionRationaleDialog(
                listOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA), false,
                { permitted = true; pending = false; starts++; monitoring = true }, { pending = false })
            RealtimeEnvironmentCard(if (monitoring) 42f else null, if (monitoring) 12f else null)
            if (report) RecentReportCard(88, "模拟报告摘要") { opens++ }
        } } }
        compose.onNodeWithText("未监测").assertIsDisplayed()
        compose.onNodeWithText("开始监测").performClick()
        compose.runOnIdle { assertTrue(pending); assertEquals(0, starts); assertFalse(monitoring) }
        // Production permission rationale emits an intent; test supplies a fake grant, no system permission UI.
        compose.onNodeWithText("授权").performClick()
        compose.onNodeWithText("停止监测").assertIsDisplayed()
        compose.onNodeWithText("噪音：42.0 dB（估算值）").assertIsDisplayed()
        compose.onNodeWithText("光照：12.0 lux（估算值）").assertIsDisplayed()
        compose.onNodeWithText("停止监测").performClick()
        compose.onNodeWithText("未监测").assertIsDisplayed()
        compose.onNodeWithText("最近睡眠评分").assertIsDisplayed()
        compose.onNodeWithText("88 / 100").assertIsDisplayed()
        compose.onNodeWithText("打开报告").performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, stops); assertEquals(1, opens) }
    }

    @Test fun privacyAgree_onlyCallbackThenPersistedStateUnlocksContent() {
        var snapshot by mutableStateOf<SettingsSnapshot?>(SettingsSnapshot())
        var agrees = 0
        compose.setContent { MaterialTheme {
            PrivacyConsentContent(snapshot, { agrees++ }, {}) { Text("应用内容") }
        } }
        compose.onNodeWithText("同意并保存").performClick()
        compose.runOnIdle { assertEquals(1, agrees) }
        compose.onNodeWithText("应用内容").assertDoesNotExist()
        compose.runOnIdle { snapshot = SettingsSnapshot(privacyConsentVersion = SettingsSnapshot.PRIVACY_POLICY_VERSION) }
        compose.onNodeWithText("应用内容").assertIsDisplayed()
        compose.onNodeWithText("同意并保存").assertDoesNotExist()
        compose.runOnIdle { snapshot = SettingsSnapshot() }
        compose.onNodeWithText("同意并保存").assertIsDisplayed()
    }

    @Test fun privacyDecline_isExitIntentWithoutConsent() {
        var declines = 0
        var agrees = 0
        compose.setContent { MaterialTheme { PrivacyPolicyDialog({ agrees++ }, { declines++ }) } }
        compose.onNodeWithText("拒绝并退出").performClick()
        compose.runOnIdle { assertEquals(1, declines); assertEquals(0, agrees) }
    }

    @Test fun loadingSnapshot_neitherPolicyFlashNorContent() {
        compose.setContent { MaterialTheme { PrivacyConsentContent(null, {}, {}) { Text("应用内容") } } }
        compose.onNodeWithText("同意并保存").assertDoesNotExist()
        compose.onNodeWithText("应用内容").assertDoesNotExist()
    }

    @Test fun readOnlyPrivacy_hasCloseWithoutAgreeDecline() {
        var closes = 0
        compose.setContent { MaterialTheme { PrivacyPolicyDialog({}, { closes++ }, readOnly = true) } }
        compose.onNodeWithText("同意并保存").assertDoesNotExist()
        compose.onNodeWithText("拒绝并退出").assertDoesNotExist()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test fun headphoneHighBanner_realRiskChangesAndStop() {
        var state by mutableStateOf(com.GXR806.envhealth.data.sensor.volume.HeadphoneGuardState(
            running = true,
            reading = com.GXR806.envhealth.data.sensor.volume.VolumeReading(
                com.GXR806.envhealth.domain.model.DeviceType.WIRED, "有线耳机", 85f, true, 0L, 0L),
            risk = com.GXR806.envhealth.domain.analysis.RiskEvaluator().evaluate(0, 85f, 0)))
        compose.setContent { MaterialTheme { HeadphoneRiskBanner(state) } }
        compose.onNodeWithText("音量过高，请及时降低").assertIsDisplayed()
        compose.onNodeWithText("健康通知设置").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(risk = com.GXR806.envhealth.domain.analysis.RiskEvaluator().evaluate(0, 50f, 0)) }
        compose.onNodeWithText("音量过高，请及时降低").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(risk = com.GXR806.envhealth.domain.analysis.RiskEvaluator().evaluate(0, 0f, 41 * 60 * 60_000L),
            weekMs = 41 * 60 * 60_000L, reading = null) }
        compose.onNodeWithText("收听需休息").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(running = false) }
        compose.onNodeWithText("收听需休息").assertDoesNotExist()
    }

    @Test fun productionThemeThreeChoices_emitSelectedModes() {
        var mode by mutableStateOf(SettingsSnapshot.THEME_MODE_SYSTEM)
        compose.setContent { MaterialTheme { ThemeModeOptions(mode, true) { mode = it } } }
        compose.onNodeWithText("跟随系统").assertIsSelected()
        compose.onNodeWithText("浅色").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(SettingsSnapshot.THEME_MODE_LIGHT, mode) }
        compose.onNodeWithText("深色").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(SettingsSnapshot.THEME_MODE_DARK, mode) }
        compose.onNodeWithText("跟随系统").performClick().assertIsSelected()
    }
}
