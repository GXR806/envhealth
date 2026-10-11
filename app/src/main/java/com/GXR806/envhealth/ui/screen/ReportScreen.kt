package com.GXR806.envhealth.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import com.GXR806.envhealth.ui.component.*
import com.GXR806.envhealth.ui.viewmodel.HistoryViewModel
import com.GXR806.envhealth.ui.viewmodel.HistoryUiState
import com.GXR806.envhealth.domain.analysis.HistoryPeriod
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.domain.model.AdviceItem
import com.GXR806.envhealth.domain.model.SleepReport
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.ui.viewmodel.ReportUiState
import com.GXR806.envhealth.ui.viewmodel.ReportViewModel
import kotlin.math.roundToInt

/**
 * 报告页面
 * 
 * 功能：
 * 1. 睡眠会话控制（开始/结束/取消）
 * 2. 显示睡眠报告（圆形评分图表 + 三维评分）
 * 3. 显示健康建议列表（可展开/收起）
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    historyViewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val latestReport by viewModel.latestReport.collectAsState()
    val latestAdvice by viewModel.latestAdvice.collectAsState()
    val history by historyViewModel.state.collectAsState()
    val period by historyViewModel.period.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val data = (history as? HistoryUiState.Ready)?.data
    val selected = data?.reports?.firstOrNull { it.report.id == selectedId }
    // 维护、删除或范围变化使选中记录立即失效，不展示 remember 的旧报告。
    androidx.compose.runtime.LaunchedEffect(data?.reports, history) {
        if (selectedId != null && selected == null) selectedId = null
    }
    BackHandler(enabled = selected != null) { selectedId = null }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selected != null) {
            item { androidx.compose.material3.TextButton(onClick = { selectedId = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("返回报告列表") } }
            item { Text("历史报告详情", style = MaterialTheme.typography.headlineMedium) }
            item { Text("会话开始：${formatHistoryTimestamp(selected.timestamp)}") }
            item { ScoreCircleCard(selected.report) }
            item { ThreeDimensionScoreCard(selected.report) }
            item { Text("历史报告仅展示持久评分和摘要；建议未关联报告，不混用当前建议。", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item { Text("健康趋势与睡眠报告", style = MaterialTheme.typography.headlineMedium) }
            item {
                SessionControlSection(currentSession, uiState, viewModel::startSleepSession,
                    viewModel::endSleepSession, viewModel::cancelSleepSession)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryPeriod.entries.forEach { option ->
                        androidx.compose.material3.FilterChip(selected = period == option,
                            onClick = { historyViewModel.selectPeriod(option) },
                            label = { Text(option.title) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = { historyViewModel.move(-1) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上一范围") }
                    androidx.compose.material3.TextButton(onClick = historyViewModel::today, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("回到当前") }
                    androidx.compose.material3.TextButton(onClick = { historyViewModel.move(1) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("下一范围") }
                }
            }
            when (val current = history) {
                HistoryUiState.Loading -> item { LoadingSkeleton() }
                is HistoryUiState.Error -> item { ErrorState(current.message, historyViewModel::retry) }
                is HistoryUiState.Ready -> {
                    val charts = current.data.charts
                    item { Text("${formatHistoryTimestamp(current.data.range.start)} 至 ${formatHistoryTimestamp(current.data.range.end)}（不含结束）", style = MaterialTheme.typography.bodySmall) }
                    item { Text("噪音与光照特征采样只保留7天；耳机与报告保留90天。缺失不补零。", style = MaterialTheme.typography.bodySmall) }
                    item { HistoryChart("噪音趋势", "估算值 dB · 桶内采样 Leq 算术均值", charts.noise) }
                    item { HistoryChart("光照分布", "估算值 lux 区间 · 采样数量（次）", charts.light, bars = true) }
                    item { HistoryChart("耳机时长", "已保存播放区间（估算值，分钟）", charts.headphone, bars = true, stacked = true) }
                    item { HistoryChart("睡眠评分趋势", "综合评分（0–100）· 桶内报告均值", charts.sleep) }
                    item { Text("历史报告", style = MaterialTheme.typography.titleLarge) }
                    if (current.data.reports.isEmpty()) item { EmptyState("所选范围没有睡眠报告，请完成一次睡眠监测") }
                    items(current.data.reports, key = { "历史-${it.report.id}" }) { timed ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { selectedId = timed.report.id }) {
                            Column(Modifier.heightIn(min = 48.dp).padding(16.dp)) {
                                Text(formatHistoryTimestamp(timed.timestamp), style = MaterialTheme.typography.titleMedium)
                                Text("综合评分 ${timed.report.totalScore.roundToInt()} / 100 · 查看详情")
                            }
                        }
                    }
                }
            }
            item { Text("最新睡眠报告", style = MaterialTheme.typography.titleLarge) }
            latestReport?.let { report ->
                item { ScoreCircleCard(report) }
                item { ThreeDimensionScoreCard(report) }
                item { Text("健康建议", style = MaterialTheme.typography.titleLarge) }
                items(latestAdvice) { AdviceCard(it) }
                if (latestAdvice.isEmpty()) item { EmptyState("暂无建议") }
            } ?: item { EmptyState("请开始并完成一次睡眠监测") }
        }
    }
}

private fun formatHistoryTimestamp(timestamp: Long): String = java.time.Instant.ofEpochMilli(timestamp)
    .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

/**
 * 会话控制区域
 */
@Composable
private fun SessionControlSection(
    currentSession: SleepSession?,
    uiState: ReportUiState,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onCancelSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState is ReportUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("处理中...")
                }
                
                currentSession != null -> {
                    Text(
                        text = "睡眠监测进行中",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "开始时间: ${formatTimestamp(currentSession.startTime)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onEndSession,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("结束监测")
                        }
                        OutlinedButton(
                            onClick = onCancelSession,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }
                    }
                }
                
                else -> {
                    Text(
                        text = "开始睡眠监测",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStartSession,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始")
                    }
                }
            }
            
            // 错误提示
            if (uiState is ReportUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 综合评分圆形图表
 */
@Composable
private fun ScoreCircleCard(
    report: SleepReport,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(report.totalScore.roundToInt(), label = "综合评分")
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "综合评分",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 圆形评分显示
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "$animatedScore",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = getScoreLabel(report.totalScore),
                style = MaterialTheme.typography.titleMedium,
                color = getScoreColor(report.totalScore)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 三维评分卡片
 */
@Composable
private fun ThreeDimensionScoreCard(
    report: SleepReport,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "详细评分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ScoreItem(label = "噪音环境", score = report.noiseScore)
            Spacer(modifier = Modifier.height(8.dp))
            ScoreItem(label = "光照环境", score = report.lightScore)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (report.regularityScore != null) {
                ScoreItem(label = "作息规律", score = report.regularityScore)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "作息规律",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "数据不足",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 评分项
 */
@Composable
private fun ScoreItem(
    label: String,
    score: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${score.roundToInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = getScoreColor(score)
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(getScoreColor(score))
            )
        }
    }
}

/**
 * 建议卡片
 */
@Composable
private fun AdviceCard(
    advice: AdviceItem,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = advice.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (advice.category) {
                            com.GXR806.envhealth.domain.model.AdviceCategory.NOISE -> "噪音"
                            com.GXR806.envhealth.domain.model.AdviceCategory.LIGHT -> "光照"
                            com.GXR806.envhealth.domain.model.AdviceCategory.SLEEP -> "睡眠"
                            com.GXR806.envhealth.domain.model.AdviceCategory.HEADPHONE -> "耳机"
                            com.GXR806.envhealth.domain.model.AdviceCategory.GENERAL -> "综合健康"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = advice.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ========== 辅助函数 ==========

@Composable
private fun getScoreColor(score: Float): Color {
    return when {
        score >= 80f -> MaterialTheme.colorScheme.primary
        score >= 60f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
}

private fun getScoreLabel(score: Float): String {
    return when {
        score >= 80f -> "优秀"
        score >= 60f -> "良好"
        score >= 40f -> "一般"
        else -> "较差"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}
