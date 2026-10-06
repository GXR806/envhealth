package com.GXR806.envhealth.ui.screen

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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val latestReport by viewModel.latestReport.collectAsState()
    val latestAdvice by viewModel.latestAdvice.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "睡眠环境报告",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 会话控制区域
        SessionControlSection(
            currentSession = currentSession,
            uiState = uiState,
            onStartSession = { viewModel.startSleepSession() },
            onEndSession = { viewModel.endSleepSession() },
            onCancelSession = { viewModel.cancelSleepSession() }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 报告显示区域
        if (latestReport != null) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 综合评分圆形图表
                item {
                    ScoreCircleCard(report = latestReport!!)
                }
                
                // 三维评分卡片
                item {
                    ThreeDimensionScoreCard(report = latestReport!!)
                }
                
                // 健康建议列表
                item {
                    Text(
                        text = "健康建议",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(latestAdvice) { advice ->
                    AdviceCard(advice = advice)
                }
                
                // 空建议提示
                if (latestAdvice.isEmpty()) {
                    item {
                        Text(
                            text = "暂无建议",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        } else {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无报告数据\n请开始一次睡眠监测",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
                    .background(getScoreColor(report.totalScore))
            ) {
                Text(
                    text = "${report.totalScore.roundToInt()}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
                        text = advice.category.name,
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

private fun getScoreColor(score: Float): Color {
    return when {
        score >= 80f -> Color(0xFF4CAF50)  // Green
        score >= 60f -> Color(0xFFFFC107)  // Amber
        score >= 40f -> Color(0xFFFF9800)  // Orange
        else -> Color(0xFFF44336)  // Red
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
