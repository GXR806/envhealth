package com.GXR806.envhealth.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 光照采样数据实体
 * 原始采样保留 7 天
 */
@Entity(tableName = "light_samples")
data class LightSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    /** 采样时间戳（毫秒） */
    val timestamp: Long,
    
    /** 平均亮度（0-255，从图像计算） */
    val avgLuma: Float,
    
    /** 估算照度值（lux，估算值），null 表示降级模式 */
    val estimatedLux: Float?,
    
    /** 光照等级（枚举名称字符串） */
    val level: String
)
