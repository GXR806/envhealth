package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.sensor.audio.NoiseFrame
import com.GXR806.envhealth.domain.model.NoiseLevel
import com.GXR806.envhealth.util.Constants
import com.GXR806.envhealth.util.LogX
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 噪音分析器
 *
 * 功能：
 * - 滑动窗口统计：Leq（1s/1min）、L10、L90
 * - 突发噪声事件检测
 * - NoiseLevel 分级
 */
class NoiseAnalyzer {
    companion object {
        private const val TAG = "NoiseAnalyzer"
        
        // 窗口大小
        private const val WINDOW_SIZE_1S = 10  // 10 帧 = 1 秒
        private const val WINDOW_SIZE_1MIN = 600  // 600 帧 = 1 分钟
        
        // 突发噪声检测阈值
        private val BURST_THRESHOLD = Constants.Audio.BURST_NOISE_THRESHOLD
    }

    // 滑动窗口（存储最近的帧）
    private val frames1s = ArrayDeque<NoiseFrame>(WINDOW_SIZE_1S)
    private val frames1min = ArrayDeque<NoiseFrame>(WINDOW_SIZE_1MIN)

    /**
     * 添加新的噪音帧进行分析
     *
     * @param frame 噪音帧
     * @return 分析结果
     */
    fun analyze(frame: NoiseFrame): NoiseAnalysisResult {
        // 添加到滑动窗口
        addFrame(frame)

        // 计算统计指标
        val leq1s = calculateLeq(frames1s)
        val leq1min = calculateLeq(frames1min)
        // Both percentile ranks use the same ordered window; do not sort it twice.
        val sorted = frames1min.map { it.dbA }.sorted()
        val l10 = sorted[((1.0 - 0.10) * sorted.size).toInt().coerceIn(0, sorted.lastIndex)]
        val l90 = sorted[((1.0 - 0.90) * sorted.size).toInt().coerceIn(0, sorted.lastIndex)]

        // 检测突发噪声
        val isBurst = detectBurst(frame)

        // 判断噪音等级
        val level = classifyNoiseLevel(frame.dbA)

        return NoiseAnalysisResult(
            timestamp = frame.timestamp,
            currentDb = frame.dbA,
            leq1s = leq1s,
            leq1min = leq1min,
            l10 = l10,
            l90 = l90,
            isBurst = isBurst,
            level = level
        )
    }

    /**
     * 添加帧到滑动窗口
     */
    private fun addFrame(frame: NoiseFrame) {
        // 1秒窗口
        if (frames1s.size >= WINDOW_SIZE_1S) {
            frames1s.removeFirst()
        }
        frames1s.addLast(frame)

        // 1分钟窗口
        if (frames1min.size >= WINDOW_SIZE_1MIN) {
            frames1min.removeFirst()
        }
        frames1min.addLast(frame)
    }

    /**
     * 计算等效连续声级 Leq
     *
     * Leq = 10 * log10( (1/N) * Σ(10^(Li/10)) )
     *
     * @param frames 帧列表
     * @return Leq (dB)
     */
    private fun calculateLeq(frames: Collection<NoiseFrame>): Float {
        if (frames.isEmpty()) return 0f

        var sum = 0.0
        for (frame in frames) {
            sum += 10.0.pow(frame.dbA / 10.0)
        }

        val mean = sum / frames.size
        return (10.0 * log10(mean)).toFloat()
    }

    /**
     * 检测突发噪声事件
     *
     * 条件：1秒内最大分贝变化 > 15dB
     *
     * @param currentFrame 当前帧
     * @return 是否为突发噪声
     */
    private fun detectBurst(currentFrame: NoiseFrame): Boolean {
        // 1秒窗口数据不足，无法判断
        if (frames1s.size < 2) return false

        // 计算 1 秒内的最小分贝和最大分贝
        val minDb = frames1s.minOf { it.dbA }
        val maxDb = frames1s.maxOf { it.dbA }
        
        // 计算 1 秒内的最大变化
        val delta = maxDb - minDb

        // 判断是否为突发噪声：1秒内变化超过阈值即可
        val isBurst = delta > BURST_THRESHOLD

        if (isBurst) {
            LogX.i(TAG, "检测到突发噪声: 当前=${currentFrame.dbA.toInt()} dB, 1秒内变化=${delta.toInt()} dB (${minDb.toInt()}→${maxDb.toInt()})")
        }

        return isBurst
    }

    /**
     * 判断噪音等级
     *
     * @param dbA A 计权声压级 (dB)
     * @return 噪音等级
     */
    private fun classifyNoiseLevel(dbA: Float): NoiseLevel {
        return when {
            dbA < Constants.NoiseThreshold.QUIET_MAX -> NoiseLevel.QUIET
            dbA < Constants.NoiseThreshold.NORMAL_MAX -> NoiseLevel.NORMAL
            dbA < Constants.NoiseThreshold.MODERATE_MAX -> NoiseLevel.MODERATE
            dbA < 80f -> NoiseLevel.LOUD
            else -> NoiseLevel.VERY_LOUD
        }
    }

    /**
     * 重置分析器状态
     */
    fun reset() {
        frames1s.clear()
        frames1min.clear()
        LogX.i(TAG, "分析器状态已重置")
    }
}

/**
 * 噪音分析结果
 */
data class NoiseAnalysisResult(
    /** 时间戳 */
    val timestamp: Long,
    
    /** 当前分贝值（A 计权） */
    val currentDb: Float,
    
    /** 1秒等效连续声级 */
    val leq1s: Float,
    
    /** 1分钟等效连续声级 */
    val leq1min: Float,
    
    /** L10: 有 10% 的时间超过该值 */
    val l10: Float,
    
    /** L90: 有 90% 的时间超过该值 */
    val l90: Float,
    
    /** 是否为突发噪声 */
    val isBurst: Boolean,
    
    /** 噪音等级 */
    val level: NoiseLevel
)
