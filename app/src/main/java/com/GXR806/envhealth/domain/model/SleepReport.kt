package com.GXR806.envhealth.domain.model

/**
 * 睡眠报告领域模型
 * 
 * @property sessionId 关联的睡眠会话 ID
 * @property noiseScore 噪音环境评分（0-100）
 * @property lightScore 光照环境评分（0-100）
 * @property regularityScore 规律性评分（0-100）
 * @property totalScore 综合评分（0-100）
 * @property summary 报告摘要文本
 */
data class SleepReport(
    val sessionId: Long,
    val noiseScore: Float,
    val lightScore: Float,
    val regularityScore: Float,
    val totalScore: Float,
    val summary: String
)
