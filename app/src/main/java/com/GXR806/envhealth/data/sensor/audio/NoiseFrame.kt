package com.GXR806.envhealth.data.sensor.audio

/**
 * 噪音帧数据
 *
 * 每 100ms 采样一次，包含原始 RMS 和估算声压级
 */
data class NoiseFrame(
    /** 时间戳（毫秒） */
    val timestamp: Long,
    
    /** dBFS（Full Scale）：相对于数字信号满量程的分贝值 */
    val dbFs: Float,
    
    /** dB SPL（Sound Pressure Level）：估算的声压级（考虑校准偏移） */
    val dbSpl: Float,
    
    /** A 计权后的声压级 */
    val dbA: Float,
    
    /** 原始 RMS 值 */
    val rms: Float
)
