package com.GXR806.envhealth.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 睡眠报告数据实体
 * 保留 90 天
 */
@Entity(
    tableName = "sleep_reports",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SleepReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    /** 关联的睡眠会话 ID */
    val sessionId: Long,
    
    /** 噪音环境评分（0-100） */
    val noiseScore: Float,
    
    /** 光照环境评分（0-100） */
    val lightScore: Float,
    
    /** 规律性评分（0-100），null 表示数据不足 */
    val regularityScore: Float?,
    
    /** 综合评分（0-100） */
    val totalScore: Float,
    
    /** 报告摘要文本 */
    val summary: String
)
