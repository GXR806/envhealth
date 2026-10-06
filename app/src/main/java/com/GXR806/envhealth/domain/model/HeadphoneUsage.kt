package com.GXR806.envhealth.domain.model

/**
 * 耳机使用记录领域模型
 * 
 * @property id 记录唯一标识
 * @property startTime 开始时间戳（毫秒）
 * @property endTime 结束时间戳（毫秒，null 表示进行中）
 * @property avgVolumePercent 平均音量百分比（0-100）
 * @property maxVolumePercent 最大音量百分比（0-100）
 * @property deviceType 设备类型
 */
data class HeadphoneUsage(
    val id: Long = 0L,
    val startTime: Long,
    val endTime: Long? = null,
    val avgVolumePercent: Float,
    val maxVolumePercent: Float,
    val deviceType: DeviceType
)
