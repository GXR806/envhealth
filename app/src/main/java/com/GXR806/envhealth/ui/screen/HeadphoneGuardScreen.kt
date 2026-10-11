package com.GXR806.envhealth.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.service.EnvMonitorService
import com.GXR806.envhealth.ui.viewmodel.HeadphoneGuardViewModel
import com.GXR806.envhealth.domain.analysis.HeadphoneRisk

@Composable
fun HeadphoneGuardScreen(viewModel: HeadphoneGuardViewModel, onGoHome: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    fun minutes(ms: Long) = "${ms / 60_000} 分 ${ms / 1000 % 60} 秒"
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("耳机守护", style = MaterialTheme.typography.headlineMedium)
        Text("守护随环境监测运行。请到首页授权麦克风、相机及通知并手动开始监测；不会自动启动。")
        if (!state.running) {
            Text("守护未启动")
            Button(onClick = onGoHome) { Text("前往首页启动监测") }
        } else {
            Button(onClick = { EnvMonitorService.stopMonitoring(context) }) { Text("停止全部监测与守护") }
            Text(state.reading?.deviceLabel ?: "正在读取耳机状态")
            if (state.reading?.device == null) Text("请连接有线、USB 或蓝牙 A2DP 耳机并播放媒体。")
            else Text(if (state.reading?.playing == true) "正在收听（估算）" else "等待播放或短暂暂停")
            Text("媒体音量：${state.reading?.percent?.toInt() ?: 0}%（系统档位，不是 dB）")
        }
        com.GXR806.envhealth.ui.component.HeadphoneRiskBanner(state)
        Text("本次实际收听（估算）：${minutes(state.durationMs)}")
        Text("本次时间加权平均音量：${state.avgPercent.toInt()}%；最大：${state.maxPercent.toInt()}%")
        Text("今日累计（估算）：${minutes(state.todayMs)}")
        Text("近 7 天累计（估算）：${minutes(state.weekMs)}")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("风险提醒：" + when (state.risk.level) {
                    HeadphoneRisk.SAFE -> "暂无触发"
                    HeadphoneRisk.LOW -> "低"
                    HeadphoneRisk.MEDIUM -> "中"
                    HeadphoneRisk.HIGH -> "高"
                }, style = MaterialTheme.typography.titleMedium)
                state.risk.reasons.forEach { Text("• $it") }
            }
        }
        state.error?.let { com.GXR806.envhealth.ui.component.ErrorState(it, viewModel::retry) }
        Text("播放状态与音量使用原有每 5 秒刷新兜底；实际音量变化即重新检查播放。连接状态不保证媒体实际路由到耳机，时长仅为估算。暂停不计时，超过 2 分钟结束本次。")
        Text("百分比不能换算为声压级；40 小时只是产品提醒阈值，不是 WHO 或医学安全保证。")
    }
}
