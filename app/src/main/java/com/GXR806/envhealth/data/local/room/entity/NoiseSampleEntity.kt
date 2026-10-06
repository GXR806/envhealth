package com.GXR806.envhealth.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 噪音采样数据实体
 * 原始采样保留 7 天
 */
@Entity(tableName = "noise_samples")
data class NoiseSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    /** 采样时间戳（毫秒） */
    val timestamp: Long,
    
    /** 等效连续声级（dB，估算值） */
    val leqDb: Float,
    
    /** 采样周期内最大值（dB，估算值） */
    val maxDb: Float,
    
    /** 峰值（dB，估算值） */
    val peakDb: Float,
    
    /** 是否为噪音事件（超过阈值） */
    val isEvent: Boolean
)
