package com.GXR806.envhealth.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 耳机使用记录数据实体
 * 保留 90 天
 */
@Entity(tableName = "headphone_usage")
data class HeadphoneUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    /** 开始时间戳（毫秒） */
    val startTime: Long,
    
    /** 结束时间戳（毫秒，null 表示进行中） */
    val endTime: Long?,
    
    /** 平均音量百分比（0-100） */
    val avgVolumePercent: Float,
    
    /** 最大音量百分比（0-100） */
    val maxVolumePercent: Float,
    
    /** 设备类型（枚举名称字符串） */
    val deviceType: String
)
