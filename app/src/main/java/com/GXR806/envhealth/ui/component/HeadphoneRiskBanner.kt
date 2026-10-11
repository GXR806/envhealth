package com.GXR806.envhealth.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.data.sensor.volume.HeadphoneGuardState
import com.GXR806.envhealth.domain.analysis.HeadphoneRisk
import com.GXR806.envhealth.notification.NotificationHelper

/** 应用内风险不是额外系统通知，不受通知冷却或静默隐藏；停止立即撤销。 */
@Composable
fun HeadphoneRiskBanner(state: HeadphoneGuardState, modifier: Modifier = Modifier) {
    if (!state.running || state.risk.level != HeadphoneRisk.HIGH) return
    val listening = state.reading?.device != null && state.reading.playing
    val weekly = state.weekMs > 40 * 60 * 60_000L
    if (!listening && !weekly) return
    val title = if (listening && (state.reading?.percent ?: 0f) > 80f) "音量过高，请及时降低" else "收听需休息"
    val reasons = if (listening) state.risk.reasons else state.risk.reasons.filter { it.contains("近 7 天") }
    // 只播报稳定原因，不包含每5秒改变的秒数/累计值。
    val announcement = "$title。${reasons.joinToString("；")}"
    val context = LocalContext.current
    Surface(modifier.fillMaxWidth().semantics {
        contentDescription = announcement
        liveRegion = LiveRegionMode.Polite
    }, color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            reasons.forEach { Text(it) }
            Text("媒体百分比不是声压级；降低音量不能替代休息。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { NotificationHelper.openHealthChannelSettings(context) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("健康通知设置") }
        }
    }
}
