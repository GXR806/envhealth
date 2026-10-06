package com.GXR806.envhealth.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 睡眠会话数据实体
 * 保留 90 天
 */
@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    /** 开始时间戳（毫秒） */
    val startTime: Long,
    
    /** 结束时间戳（毫秒，null 表示进行中） */
    val endTime: Long?,
    
    /** 会话类型（MANUAL/AUTO） */
    val type: String,
    
    /** 会话状态（ONGOING/COMPLETED/CANCELLED） */
    val status: String
)
