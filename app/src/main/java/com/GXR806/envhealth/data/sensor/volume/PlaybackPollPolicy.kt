package com.GXR806.envhealth.data.sensor.volume

/** 共用原5秒tick，不新增计时任务；实际音量变化/设备事件立即重新读取。 */
internal object PlaybackPollPolicy {
    const val INTERVAL_MS = 5_000L
    fun shouldPoll(force: Boolean, changed: Boolean, last: Long, now: Long): Boolean =
        force || changed || last == Long.MIN_VALUE || now - last >= INTERVAL_MS
}
