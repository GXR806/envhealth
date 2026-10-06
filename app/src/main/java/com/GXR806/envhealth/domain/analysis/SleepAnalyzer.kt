package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.repository.LightRepository
import com.GXR806.envhealth.data.repository.NoiseRepository
import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.model.LightSample
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.util.LogX
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 睡眠分析器
 * 
 * 职责：基于噪音/光照时序数据计算三项评分
 * 1. 噪音评分（0-100）
 * 2. 光照评分（0-100）
 * 3. 规律性评分（0-100）
 * 4. 综合评分（加权和）
 */
class SleepAnalyzer(
    private val noiseRepository: NoiseRepository,
    private val lightRepository: LightRepository,
    private val sleepRepository: SleepRepository
) {
    companion object {
        private const val TAG = "SleepAnalyzer"
        
        // 评分权重
        private const val WEIGHT_NOISE = 0.45f
        private const val WEIGHT_LIGHT = 0.35f
        private const val WEIGHT_REGULARITY = 0.20f
        
        // 规律性评分数据要求
        private const val MIN_SESSIONS_FOR_REGULARITY = 3
        private const val TARGET_SESSIONS_FOR_REGULARITY = 7
    }
    
    /**
     * 分析睡眠会话
     * 
     * @param session 睡眠会话
     * @return 分析结果
     */
    suspend fun analyze(session: SleepSession): SleepAnalysisResult {
        return try {
            val startTime = session.startTime
            val endTime = session.endTime ?: System.currentTimeMillis()
            
            // 计算噪音评分
            val noiseStats = calculateNoiseStats(startTime, endTime)
            val noiseScore = calculateNoiseScore(noiseStats)
            
            // 计算光照评分
            val lightStats = calculateLightStats(startTime, endTime)
            val lightScore = calculateLightScore(lightStats)
            
            // 计算规律性评分
            val regularityStats = calculateRegularityStats(session)
            val regularityScore = regularityStats?.let { calculateRegularityScore(it) }
            
            // 计算综合评分
            val totalScore = calculateTotalScore(noiseScore, lightScore, regularityScore)
            
            LogX.i(TAG, "睡眠分析完成: 噪音=${noiseScore}, 光照=${lightScore}, 规律性=${regularityScore}, 总分=${totalScore}")
            
            SleepAnalysisResult(
                noiseScore = noiseScore,
                lightScore = lightScore,
                regularityScore = regularityScore,
                totalScore = totalScore,
                noiseStats = noiseStats,
                lightStats = lightStats,
                regularityStats = regularityStats
            )
        } catch (e: Exception) {
            LogX.e(TAG, "睡眠分析失败", e)
            throw e
        }
    }
    
    // ========== 噪音评分 ==========
    
    /**
     * 计算噪音统计数据
     */
    private suspend fun calculateNoiseStats(startTime: Long, endTime: Long): NoiseStats {
        val samples = noiseRepository.getSamplesByTimeRangeOnce(startTime, endTime)
        
        if (samples.isEmpty()) {
            return NoiseStats(
                avgLeq = 0f,
                maxDb = 0f,
                burstCount = 0
            )
        }
        
        val avgLeq = samples.map { it.leqDb }.average().toFloat()
        val maxDb = samples.maxOfOrNull { it.maxDb } ?: 0f
        val burstCount = samples.count { it.isEvent }
        
        return NoiseStats(
            avgLeq = avgLeq,
            maxDb = maxDb,
            burstCount = burstCount
        )
    }
    
    /**
     * 计算噪音评分（0-100）
     * 
     * 公式：
     * noiseScore = 100 - (avgLeqPenalty + maxDbPenalty + burstEventPenalty)
     * 
     * 其中：
     * - avgLeqPenalty = min((avgLeq - 30) * 2, 40)      // 平均分贝惩罚（权重 40%）
     * - maxDbPenalty = min((maxDb - 50) * 1, 30)         // 峰值分贝惩罚（权重 30%）
     * - burstEventPenalty = min(burstCount * 3, 30)      // 突发事件惩罚（权重 30%）
     */
    private fun calculateNoiseScore(stats: NoiseStats): Float {
        val avgLeqPenalty = min((stats.avgLeq - 30f) * 2f, 40f).coerceAtLeast(0f)
        val maxDbPenalty = min((stats.maxDb - 50f) * 1f, 30f).coerceAtLeast(0f)
        val burstEventPenalty = min(stats.burstCount * 3f, 30f)
        
        val score = 100f - (avgLeqPenalty + maxDbPenalty + burstEventPenalty)
        return score.coerceIn(0f, 100f)
    }
    
    // ========== 光照评分 ==========
    
    /**
     * 计算光照统计数据
     */
    private suspend fun calculateLightStats(startTime: Long, endTime: Long): LightStats {
        val samples = lightRepository.getSamplesByTimeRangeOnce(startTime, endTime)
        
        if (samples.isEmpty()) {
            return LightStats(
                avgLux = 0f,
                stdDevLux = 0f,
                preSleepTrendDecreasing = false
            )
        }
        
        // 计算平均 lux
        val luxValues = samples.mapNotNull { it.estimatedLux }
        val avgLux = if (luxValues.isNotEmpty()) {
            luxValues.average().toFloat()
        } else {
            // 降级：使用相对亮度估算
            samples.map { it.avgLuma / 255f * 100f }.average().toFloat()
        }
        
        // 计算标准差
        val stdDevLux = if (luxValues.size > 1) {
            val mean = luxValues.average()
            val variance = luxValues.map { (it - mean).pow(2) }.average()
            sqrt(variance).toFloat()
        } else {
            0f
        }
        
        // 检查睡前 1 小时光照是否下降
        val preSleepTrendDecreasing = checkPreSleepTrend(samples, startTime)
        
        return LightStats(
            avgLux = avgLux,
            stdDevLux = stdDevLux,
            preSleepTrendDecreasing = preSleepTrendDecreasing
        )
    }
    
    /**
     * 检查睡前 1 小时光照趋势
     */
    private fun checkPreSleepTrend(samples: List<LightSample>, startTime: Long): Boolean {
        val oneHourBeforeSleep = startTime - 3600_000L  // 1 小时前
        
        val preSleepSamples = samples.filter { it.timestamp >= oneHourBeforeSleep && it.timestamp <= startTime }
        
        if (preSleepSamples.size < 2) {
            return false  // 数据不足
        }
        
        // 简单趋势检测：比较前半段和后半段的平均亮度
        val midPoint = preSleepSamples.size / 2
        val firstHalf = preSleepSamples.take(midPoint)
        val secondHalf = preSleepSamples.drop(midPoint)
        
        val firstHalfAvg = firstHalf.map { it.avgLuma }.average()
        val secondHalfAvg = secondHalf.map { it.avgLuma }.average()
        
        return secondHalfAvg < firstHalfAvg  // 后半段亮度低于前半段
    }
    
    /**
     * 计算光照评分（0-100）
     * 
     * 公式：
     * lightScore = 100 - (avgLuxPenalty + fluctuationPenalty + preSleepTrendPenalty)
     * 
     * 其中：
     * - avgLuxPenalty = min((avgLux - 5) * 1.5, 40)      // 平均光照惩罚（权重 40%）
     * - fluctuationPenalty = min(stdDevLux * 2, 30)      // 波动惩罚（权重 30%）
     * - preSleepTrendPenalty = 30 if 睡前1h未下降 else 0 // 睡前趋势惩罚（权重 30%）
     */
    private fun calculateLightScore(stats: LightStats): Float {
        val avgLuxPenalty = min((stats.avgLux - 5f) * 1.5f, 40f).coerceAtLeast(0f)
        val fluctuationPenalty = min(stats.stdDevLux * 2f, 30f)
        val preSleepTrendPenalty = if (!stats.preSleepTrendDecreasing) 30f else 0f
        
        val score = 100f - (avgLuxPenalty + fluctuationPenalty + preSleepTrendPenalty)
        return score.coerceIn(0f, 100f)
    }
    
    // ========== 规律性评分 ==========
    
    /**
     * 计算规律性统计数据
     */
    private suspend fun calculateRegularityStats(currentSession: SleepSession): RegularityStats? {
        // 获取近 7 天的已完成会话（不包括当前会话）
        val sevenDaysAgo = currentSession.startTime - 7L * 24 * 3600_000L
        val recentSessions = sleepRepository.getRecentCompletedSessions(TARGET_SESSIONS_FOR_REGULARITY).first()
            .filter { it.id != currentSession.id && it.startTime >= sevenDaysAgo }
        
        if (recentSessions.size < MIN_SESSIONS_FOR_REGULARITY) {
            LogX.w(TAG, "规律性评分数据不足: 需要至少 $MIN_SESSIONS_FOR_REGULARITY 天，实际 ${recentSessions.size} 天")
            return null  // 数据不足
        }
        
        // 计算入睡时间标准差（小时）
        val sleepTimes = recentSessions.map { it.startTime }
        val sleepHours = sleepTimes.map { it % (24 * 3600_000L) / 3600_000f }  // 转换为当天的小时数
        
        val mean = sleepHours.average()
        val variance = sleepHours.map { (it - mean).pow(2) }.average()
        val stdDevHours = sqrt(variance).toFloat()
        
        return RegularityStats(
            sessionCount = recentSessions.size,
            stdDevHours = stdDevHours
        )
    }
    
    /**
     * 计算规律性评分（0-100）
     * 
     * 公式：
     * regularityScore = 100 - sleepTimeStdDevPenalty
     * 
     * 其中：
     * - sleepTimeStdDevPenalty = min(stdDevHours * 20, 100)  // 每小时标准差扣 20 分
     */
    private fun calculateRegularityScore(stats: RegularityStats): Float {
        val sleepTimeStdDevPenalty = min(stats.stdDevHours * 20f, 100f)
        val score = 100f - sleepTimeStdDevPenalty
        return score.coerceIn(0f, 100f)
    }
    
    // ========== 综合评分 ==========
    
    /**
     * 计算综合评分（0-100）
     * 
     * 公式：
     * totalScore = noiseScore * 0.45 + lightScore * 0.35 + regularityScore * 0.20
     * 
     * 如果规律性评分为 null（数据不足），则：
     * totalScore = noiseScore * 0.60 + lightScore * 0.40
     */
    private fun calculateTotalScore(noiseScore: Float, lightScore: Float, regularityScore: Float?): Float {
        return if (regularityScore != null) {
            noiseScore * WEIGHT_NOISE + lightScore * WEIGHT_LIGHT + regularityScore * WEIGHT_REGULARITY
        } else {
            // 数据不足，调整权重
            noiseScore * 0.60f + lightScore * 0.40f
        }
    }
    
    // ========== 数据类 ==========
    
    /**
     * 睡眠分析结果
     */
    data class SleepAnalysisResult(
        val noiseScore: Float,
        val lightScore: Float,
        val regularityScore: Float?,  // null 表示数据不足
        val totalScore: Float,
        val noiseStats: NoiseStats,
        val lightStats: LightStats,
        val regularityStats: RegularityStats?
    )
    
    /**
     * 噪音统计数据
     */
    data class NoiseStats(
        val avgLeq: Float,
        val maxDb: Float,
        val burstCount: Int
    )
    
    /**
     * 光照统计数据
     */
    data class LightStats(
        val avgLux: Float,
        val stdDevLux: Float,
        val preSleepTrendDecreasing: Boolean
    )
    
    /**
     * 规律性统计数据
     */
    data class RegularityStats(
        val sessionCount: Int,
        val stdDevHours: Float
    )
}
