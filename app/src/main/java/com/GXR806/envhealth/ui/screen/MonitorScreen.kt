package com.GXR806.envhealth.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.domain.model.LightLevel
import com.GXR806.envhealth.domain.model.NoiseLevel
import com.GXR806.envhealth.util.Constants
import com.GXR806.envhealth.util.LightMonitorState
import com.GXR806.envhealth.util.MonitorState
import com.GXR806.envhealth.util.NoiseMonitorState
import java.util.Locale

/**
 * 环境监测页面
 *
 * 显示实时噪音和光照数据
 */
@Composable
fun MonitorScreen(modifier: Modifier = Modifier) {
    val isMonitoring by MonitorState.isMonitoring.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isMonitoring) {
            MonitoringView()
        } else {
            com.GXR806.envhealth.ui.component.EmptyState("请在首页手动开始监测")
        }
    }
}

/**
 * 监测视图（噪音 + 光照）
 */
@Composable
private fun MonitoringView() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NoiseMonitorSection()
        
        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(32.dp))
        
        LightMonitorSection()
    }
}

/**
 * 噪音监测部分
 */
@Composable
private fun NoiseMonitorSection() {
    val analysisResult by NoiseMonitorState.analysisResult.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "噪音监测",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (analysisResult != null) {
            val result = analysisResult!!
            
            CurrentDbDisplay(db = result.currentDb, level = result.level)
            Spacer(modifier = Modifier.height(24.dp))
            
            RealTimeWaveform(
                currentDb = result.currentDb,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            StatisticsCards(
                leq1s = result.leq1s,
                leq1min = result.leq1min,
                l10 = result.l10,
                l90 = result.l90,
                isBurst = result.isBurst
            )
        } else {
            Text(
                text = "等待数据...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 光照监测部分
 */
@Composable
private fun LightMonitorSection() {
    val lightFrame by LightMonitorState.lightFrame.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "光照监测",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (lightFrame != null) {
            val frame = lightFrame!!
            
            CurrentLightDisplay(frame = frame)
            Spacer(modifier = Modifier.height(24.dp))
            
            LightStatisticsCards(frame = frame)
        } else {
            Text(
                text = "等待数据...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 当前光照值显示
 */
@Composable
private fun CurrentLightDisplay(frame: com.GXR806.envhealth.data.sensor.light.LightFrame) {
    val animatedBrightness by animateFloatAsState(
        targetValue = frame.relativeBrightness,
        animationSpec = tween(durationMillis = 300),
        label = "brightness_animation"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (frame.estimatedLux != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.0f", frame.estimatedLux),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = getLightLevelColor(frame.level)
                )
                Text(
                    text = " lux",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.0f", animatedBrightness),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = getLightLevelColor(frame.level)
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = "相对亮度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = Constants.ESTIMATED_VALUE_SUFFIX,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        LightLevelBadge(level = frame.level, isDegradedMode = frame.isDegradedMode)
    }
}

/**
 * 光照等级徽章
 */
@Composable
private fun LightLevelBadge(level: LightLevel, isDegradedMode: Boolean) {
    val text = when (level) {
        LightLevel.DARK -> "昏暗"
        LightLevel.DIM -> "较暗"
        LightLevel.MODERATE -> "适中"
        LightLevel.BRIGHT -> "明亮"
    }
    val color = getLightLevelColor(level)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f)
            )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
        
        if (isDegradedMode) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "降级模式（基于亮度估算）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 光照统计卡片
 */
@Composable
private fun LightStatisticsCards(frame: com.GXR806.envhealth.data.sensor.light.LightFrame) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "平均亮度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f", frame.avgLuma),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/ 255",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "相对亮度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f", frame.relativeBrightness),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 当前分贝值显示
 */
@Composable
private fun CurrentDbDisplay(db: Float, level: NoiseLevel) {
    val animatedDb by animateFloatAsState(
        targetValue = db,
        animationSpec = tween(durationMillis = 300),
        label = "db_animation"
    )
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.US, "%.1f", animatedDb),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = getLevelColor(level)
            )
            Text(
                text = " dB",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = Constants.ESTIMATED_VALUE_SUFFIX,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        NoiseLevelBadge(level = level)
    }
}

/**
 * 噪音等级徽章
 */
@Composable
private fun NoiseLevelBadge(level: NoiseLevel) {
    val text = when (level) {
        NoiseLevel.QUIET -> "安静"
        NoiseLevel.NORMAL -> "正常"
        NoiseLevel.MODERATE -> "偏吵"
        NoiseLevel.LOUD -> "嘈杂"
        NoiseLevel.VERY_LOUD -> "非常吵"
    }
    val color = getLevelColor(level)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 实时波形图
 */
@Composable
private fun RealTimeWaveform(
    currentDb: Float,
    modifier: Modifier = Modifier
) {
    val waveformData = remember { mutableStateListOf<Float>() }
    
    LaunchedEffect(currentDb) {
        waveformData.add(currentDb)
        if (waveformData.size > 30) {
            waveformData.removeAt(0)
        }
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = "实时噪音曲线，当前 %.1f dB，估算值".format(currentDb) }) {
            val width = size.width
            val height = size.height
            val centerY = height / 2

            if (waveformData.isNotEmpty()) {
                val path = Path()
                val pointSpacing = width / 30f
                
                path.moveTo(0f, centerY)
                waveformData.forEachIndexed { index, db ->
                    val x = index * pointSpacing
                    val normalizedDb = ((db - 30f) / 50f).coerceIn(0f, 1f)
                    val y = centerY - (normalizedDb - 0.5f) * height * 0.8f
                    path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4f)
                )
            }

            drawLine(
                color = gridColor,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )
            
            val y40 = centerY - ((40f - 30f) / 50f - 0.5f) * height * 0.8f
            val y60 = centerY - ((60f - 30f) / 50f - 0.5f) * height * 0.8f
            
            drawLine(
                color = gridColor,
                start = Offset(0f, y40),
                end = Offset(width, y40),
                strokeWidth = 1f
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, y60),
                end = Offset(width, y60),
                strokeWidth = 1f
            )
        }

        if (waveformData.isEmpty()) {
            Text(
                text = "等待数据...",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 统计指标卡片
 */
@Composable
private fun StatisticsCards(
    leq1s: Float,
    leq1min: Float,
    l10: Float,
    l90: Float,
    isBurst: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Leq (1s)",
                value = leq1s,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Leq (1min)",
                value = leq1min,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "L10",
                value = l10,
                modifier = Modifier.weight(1f),
                description = "偏高"
            )
            StatCard(
                title = "L90",
                value = l90,
                modifier = Modifier.weight(1f),
                description = "偏低"
            )
        }
        
        if (isBurst) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "⚠️ 检测到突发噪声",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 统计指标卡片
 */
@Composable
private fun StatCard(
    title: String,
    value: Float,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%.1f", value),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "dB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getLevelColor(level: NoiseLevel): Color = when (level) {
    NoiseLevel.QUIET, NoiseLevel.NORMAL -> MaterialTheme.colorScheme.primary
    NoiseLevel.MODERATE -> MaterialTheme.colorScheme.secondary
    NoiseLevel.LOUD, NoiseLevel.VERY_LOUD -> MaterialTheme.colorScheme.error
}

@Composable
private fun getLightLevelColor(level: LightLevel): Color = when (level) {
    LightLevel.DARK, LightLevel.DIM -> MaterialTheme.colorScheme.onSurfaceVariant
    LightLevel.MODERATE -> MaterialTheme.colorScheme.primary
    LightLevel.BRIGHT -> MaterialTheme.colorScheme.secondary
}
