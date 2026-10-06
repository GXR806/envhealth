package com.GXR806.envhealth.data.sensor.light

import com.GXR806.envhealth.domain.model.LightLevel

/**
 * 光照采样帧
 * 
 * @property timestamp 时间戳（ms）
 * @property avgLuma 平均亮度（0-255，YUV Y平面）
 * @property estimatedLux 估算的光照强度（lux），null 表示降级模式
 * @property relativeBrightness 相对亮度指数（0-100），降级模式时使用
 * @property level 光照等级
 * @property exposureTimeNs 曝光时间（ns），null 表示无法获取
 * @property iso ISO 感光度，null 表示无法获取
 */
data class LightFrame(
    val timestamp: Long,
    val avgLuma: Float,
    val estimatedLux: Float?,
    val relativeBrightness: Float,
    val level: LightLevel,
    val exposureTimeNs: Long?,
    val iso: Int?
) {
    /**
     * 是否为降级模式（无法获取曝光参数）
     */
    val isDegradedMode: Boolean
        get() = estimatedLux == null
}
