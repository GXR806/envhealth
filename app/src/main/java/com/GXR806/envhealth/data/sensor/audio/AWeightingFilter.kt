package com.GXR806.envhealth.data.sensor.audio

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A 计权滤波器
 *
 * 使用双二阶 IIR 滤波器实现 A 计权近似
 * 参考：IEC 61672-1:2013 标准
 *
 * A 计权是对人耳频率响应的模拟，主要衰减低频和高频成分
 * 滤波器设计参考：
 * - https://en.wikipedia.org/wiki/A-weighting
 * - Digital Audio Signal Processing (Udo Zölzer)
 *
 * 系数设计（44100 Hz 采样率）：
 * - 高通滤波器（截止频率 ~20 Hz）：衰减低频
 * - 峰值滤波器（中心频率 ~1000 Hz）：提升中频
 * - 低通滤波器（截止频率 ~20000 Hz）：衰减高频
 *
 * 简化实现：使用单个二阶段实现近似 A 计权曲线
 */
class AWeightingFilter(sampleRate: Int = 44100) {

    // 二阶 IIR 滤波器系数（Biquad）
    // H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
    
    // 系数设计：基于 44100 Hz 采样率的 A 计权近似
    // 参考：Digital filters for A-weighting (simplified implementation)
    private val b0 = 0.169994948147430
    private val b1 = 0.0
    private val b2 = -0.169994948147430
    private val a1 = -0.660010103705539
    private val a2 = 0.0

    // 状态变量（用于存储前两个样本）
    private var x1 = 0.0  // x[n-1]
    private var x2 = 0.0  // x[n-2]
    private var y1 = 0.0  // y[n-1]
    private var y2 = 0.0  // y[n-2]

    /**
     * 处理单个样本
     *
     * @param sample 输入样本
     * @return 经过 A 计权滤波后的样本
     */
    fun process(sample: Double): Double {
        // 二阶 IIR 滤波器计算
        val output = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2

        // 更新状态
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = output

        return output
    }

    /**
     * 处理一批样本
     *
     * @param samples 输入样本数组
     * @return 经过 A 计权滤波后的样本数组
     */
    fun process(samples: ShortArray): DoubleArray {
        return DoubleArray(samples.size) { i ->
            process(samples[i].toDouble())
        }
    }

    /**
     * 计算 A 计权后的 RMS
     *
     * @param samples 输入样本数组
     * @return A 计权后的 RMS 值
     */
    fun processAndCalculateRMS(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0

        var sumSquares = 0.0
        for (sample in samples) {
            val filtered = process(sample.toDouble())
            sumSquares += filtered * filtered
        }

        return sqrt(sumSquares / samples.size)
    }

    /**
     * 重置滤波器状态
     */
    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}
