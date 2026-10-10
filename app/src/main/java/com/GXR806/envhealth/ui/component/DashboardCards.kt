package com.GXR806.envhealth.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.domain.analysis.HeadphoneRisk
import com.GXR806.envhealth.ui.viewmodel.*

@Composable
fun DashboardCards(viewModel: DashboardViewModel) {
    val noise by viewModel.noise.collectAsState()
    val light by viewModel.light.collectAsState()
    val headphone by viewModel.headphone.collectAsState()
    val headphoneLoading by viewModel.headphoneLoading.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refreshHeadphones() }
    val report by viewModel.report.collectAsState()
    val db by animateFloatAsState(noise?.currentDb ?: 0f, label = "实时噪音")
    val lux by animateFloatAsState(light?.estimatedLux ?: 0f, label = "实时光照")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("环境总览", style = MaterialTheme.typography.titleLarge)
            Text("噪音：" + if (noise == null) "暂无实时数据" else "%.1f dB（估算值）".format(db), style = MaterialTheme.typography.titleMedium)
            Text("光照：" + when { light == null -> "暂无实时数据"; light?.estimatedLux == null -> "曝光参数不足，无法估算 lux"; else -> "%.1f lux（估算值）".format(lux) }, style = MaterialTheme.typography.titleMedium)
            if (noise == null && light == null) Text("手动开始监测后显示，不会自动开启硬件", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("今日耳机", style = MaterialTheme.typography.titleLarge)
            if (headphoneLoading && !headphone.running) LoadingSkeleton()
            else if (headphone.error != null) ErrorState(headphone.error!!, viewModel::refreshHeadphones)
            else {
                Text("${headphone.todayMs / 60_000} 分钟（估算值）", style = MaterialTheme.typography.headlineSmall)
                Text("风险：" + when (headphone.risk.level) {
                    HeadphoneRisk.SAFE -> "暂无触发"; HeadphoneRisk.LOW -> "低"; HeadphoneRisk.MEDIUM -> "中"; HeadphoneRisk.HIGH -> "高"
                })
                Text(headphone.risk.reasons.joinToString("；"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        when (val current = report) {
            DashboardReportState.Loading -> LoadingSkeleton()
            DashboardReportState.Error -> ErrorState("最近报告读取失败", viewModel::retry)
            is DashboardReportState.Ready -> {
                if (current.report == null) EmptyState("最近睡眠评分将在完成一次睡眠会话后显示")
                else {
                    val score by animateIntAsState(current.report.totalScore.toInt(), label = "睡眠评分")
                    Column(Modifier.padding(16.dp)) {
                        Text("最近睡眠评分", style = MaterialTheme.typography.titleLarge)
                        Text("$score / 100", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(current.report.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
