package com.GXR806.envhealth.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Intent-only production UI: permission and hardware orchestration remain in HomeScreen. */
@Composable
fun MonitorControls(isMonitoring: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Text(if (isMonitoring) "监测中..." else "未监测", style = MaterialTheme.typography.bodyLarge,
        color = if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onToggle, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (isMonitoring) "停止监测" else "开始监测")
    }
}

@Composable
fun RealtimeEnvironmentCard(noiseDb: Float?, lux: Float?, hasLight: Boolean = lux != null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("环境总览", style = MaterialTheme.typography.titleLarge)
            Text("噪音：" + if (noiseDb == null) "暂无实时数据" else "%.1f dB（估算值）".format(noiseDb), style = MaterialTheme.typography.titleMedium)
            Text("光照：" + when { !hasLight -> "暂无实时数据"; lux == null -> "曝光参数不足，无法估算 lux"; else -> "%.1f lux（估算值）".format(lux) }, style = MaterialTheme.typography.titleMedium)
            if (noiseDb == null && !hasLight) Text("手动开始监测后显示，不会自动开启硬件")
        }
    }
}

@Composable
fun RecentReportCard(score: Int, summary: String, onOpenReport: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("最近睡眠评分", style = MaterialTheme.typography.titleLarge)
        Text("$score / 100", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(summary)
        TextButton(onClick = onOpenReport, modifier = Modifier.heightIn(min = 48.dp)) { Text("打开报告") }
    }
}
