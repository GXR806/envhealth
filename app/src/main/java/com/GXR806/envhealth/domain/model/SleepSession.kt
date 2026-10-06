package com.GXR806.envhealth.domain.model

/**
 * 睡眠会话领域模型
 * 
 * @property id 会话唯一标识
 * @property startTime 开始时间戳（毫秒）
 * @property endTime 结束时间戳（毫秒，null 表示进行中）
 * @property type 会话类型（手动/自动）
 * @property status 会话状态
 */
data class SleepSession(
    val id: Long = 0L,
    val startTime: Long,
    val endTime: Long? = null,
    val type: SleepSessionType,
    val status: SleepSessionStatus
)
