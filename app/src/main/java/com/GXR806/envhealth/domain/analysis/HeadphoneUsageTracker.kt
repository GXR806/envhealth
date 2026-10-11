package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.domain.model.DeviceType
import com.GXR806.envhealth.domain.model.HeadphoneUsage

/** 串行调用的纯 Kotlin 状态机。每个实际播放区间一条记录，兼容数据库版本 1。 */
class HeadphoneUsageTracker {
    companion object { const val PAUSE_TIMEOUT_MS = 120_000L }
    var durationMs = 0L
        private set
    var maxVolumePercent = 0f
        private set
    private var weightedVolume = 0.0
    val avgVolumePercent: Float get() = if (durationMs == 0L) 0f else (weightedVolume / durationMs).toFloat()
    private var device: DeviceType? = null
    private var lastMono = 0L
    private var volume = 0f
    private var playing = false
    private var pausedAt: Long? = null
    private var segmentStart = 0L
    private var segmentDuration = 0L
    private var segmentWeighted = 0.0
    private var segmentMax = 0f
    val pending = mutableListOf<HeadphoneUsage>()

    fun update(type: DeviceType?, active: Boolean, percent: Float, mono: Long, wall: Long) {
        advance(mono)
        val expired = pausedAt?.let { mono - it > PAUSE_TIMEOUT_MS } == true
        if (device != type || expired) {
            closeSegment()
            resetSession()
            device = type
        }
        val nextPlaying = type != null && active
        if (playing && !nextPlaying) {
            closeSegment()
            pausedAt = mono
        }
        if (!playing && nextPlaying) {
            segmentStart = wall
            pausedAt = null
        }
        playing = nextPlaying
        volume = percent.coerceIn(0f, 100f)
        if (playing) {
            segmentMax = maxOf(segmentMax, volume)
            maxVolumePercent = maxOf(maxVolumePercent, volume)
        }
        lastMono = mono
    }

    private fun advance(mono: Long) {
        if (!playing) return
        val elapsed = (mono - lastMono).coerceAtLeast(0L)
        durationMs += elapsed
        segmentDuration += elapsed
        weightedVolume += elapsed * volume.toDouble()
        segmentWeighted += elapsed * volume.toDouble()
    }

    fun liveSegment(): HeadphoneUsage? = if (segmentDuration > 0L) HeadphoneUsage(
        startTime = segmentStart, endTime = segmentStart + segmentDuration,
        avgVolumePercent = (segmentWeighted / segmentDuration).toFloat(),
        maxVolumePercent = segmentMax, deviceType = device ?: DeviceType.UNKNOWN
    ) else null

    private fun closeSegment() {
        liveSegment()?.let { pending += it }
        segmentDuration = 0L
        segmentWeighted = 0.0
        segmentMax = 0f
    }

    private fun resetSession() {
        playing = false
        pausedAt = null
        durationMs = 0L
        weightedVolume = 0.0
        maxVolumePercent = 0f
    }

    fun stop(mono: Long) {
        advance(mono)
        closeSegment()
        resetSession()
        device = null
    }
}

/** 半开区间裁剪：数据库记录与尚未落库区间各计一次，跨午夜/7天边界不计整条。 */
fun clippedDuration(records: List<HeadphoneUsage>, start: Long, end: Long): Long = records.sumOf {
    (minOf(it.endTime ?: end, end) - maxOf(it.startTime, start)).coerceAtLeast(0L)
}
