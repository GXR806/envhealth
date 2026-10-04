package com.GXR806.envhealth.util

import java.time.Instant

/** 统一时间工具：持久化与跨层传递统一使用毫秒时间戳。 */
object TimeUtils {
    fun nowMillis(): Long = System.currentTimeMillis()

    fun formatIso8601(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).toString()
}
