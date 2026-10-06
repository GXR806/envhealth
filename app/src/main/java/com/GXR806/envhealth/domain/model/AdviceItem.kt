package com.GXR806.envhealth.domain.model

/**
 * 健康建议领域模型
 * 
 * @property id 建议唯一标识
 * @property category 建议类别
 * @property priority 优先级（1-5，5 最高）
 * @property title 建议标题
 * @property content 建议详细内容
 * @property triggerReason 触发原因描述
 */
data class AdviceItem(
    val id: Long = 0L,
    val category: AdviceCategory,
    val priority: Int,
    val title: String,
    val content: String,
    val triggerReason: String
)
