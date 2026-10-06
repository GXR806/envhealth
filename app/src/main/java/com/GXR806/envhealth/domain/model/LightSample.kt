package com.GXR806.envhealth.domain.model

/**
 * 光照采样领域模型
 * 
 * @property timestamp 采样时间戳（毫秒）
 * @property avgLuma 平均亮度（0-255，从图像计算）
 * @property estimatedLux 估算照度值（lux，估算值），null 表示降级模式
 * @property level 光照等级
 */
data class LightSample(
    val timestamp: Long,
    val avgLuma: Float,
    val estimatedLux: Float?,
    val level: LightLevel
)
