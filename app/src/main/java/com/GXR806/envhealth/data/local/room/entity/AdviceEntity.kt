package com.GXR806.envhealth.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 健康建议数据实体
 * 保留 90 天
 */
@Entity(tableName = "advice_items")
data class AdviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    
    /** 建议类别（枚举名称字符串） */
    val category: String,
    
    /** 优先级（1-5，5 最高） */
    val priority: Int,
    
    /** 建议标题 */
    val title: String,
    
    /** 建议详细内容 */
    val content: String,
    
    /** 触发原因描述 */
    val triggerReason: String,
    
    /** 创建时间戳（毫秒） */
    val createdAt: Long
)
