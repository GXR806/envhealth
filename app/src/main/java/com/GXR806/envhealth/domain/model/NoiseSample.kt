package com.GXR806.envhealth.domain.model

/**
 * 噪音采样领域模型
 * 
 * @property timestamp 采样时间戳（毫秒）
 * @property leqDb 等效连续声级（dB，估算值）
 * @property maxDb 采样周期内最大值（dB，估算值）
 * @property peakDb 峰值（dB，估算值）
 * @property isEvent 是否为噪音事件（超过阈值）
 */
data class NoiseSample(
    val timestamp: Long,
    val leqDb: Float,
    val maxDb: Float,
    val peakDb: Float,
    val isEvent: Boolean
)
