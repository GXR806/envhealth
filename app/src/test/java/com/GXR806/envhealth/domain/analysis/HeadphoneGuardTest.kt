package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.domain.model.DeviceType
import com.GXR806.envhealth.domain.model.HeadphoneUsage
import org.junit.Assert.*
import org.junit.Test

/** 既有 JUnit 基础设施；无需 Android 音频硬件。 */
class HeadphoneGuardTest {
    private val hour = 3_600_000L
    private val risk = RiskEvaluator()
    @Test fun allLevelsAndStrictBoundaries() {
        assertEquals(HeadphoneRisk.SAFE, risk.evaluate(hour, 60f, 40 * hour).level)
        assertEquals(HeadphoneRisk.LOW, risk.evaluate(hour, 60.01f, 0).level)
        assertEquals(HeadphoneRisk.MEDIUM, risk.evaluate(hour + 1, 60f, 0).level)
        assertEquals(HeadphoneRisk.HIGH, risk.evaluate(hour + 1, 60.01f, 0).level)
        assertEquals(HeadphoneRisk.LOW, risk.evaluate(0, 80f, 0).level)
        assertEquals(HeadphoneRisk.HIGH, risk.evaluate(0, 80.01f, 0).level)
        assertEquals(HeadphoneRisk.HIGH, risk.evaluate(0, 0f, 40 * hour + 1).level)
        assertEquals(5, risk.evaluate(hour + 1, 81f, 40 * hour + 1).reasons.size)
    }
    @Test fun pauseDoesNotCountAndTwoMinutesIsNotExpired() {
        val t = HeadphoneUsageTracker()
        t.update(DeviceType.WIRED, true, 20f, 0, 1000)
        t.update(DeviceType.WIRED, false, 20f, 60_000, 61_000)
        assertEquals(60_000L, t.durationMs)
        t.update(DeviceType.WIRED, true, 80f, 180_000, 181_000)
        t.update(DeviceType.WIRED, true, 80f, 240_000, 241_000)
        assertEquals(120_000L, t.durationMs)
        assertEquals(50f, t.avgVolumePercent, 0.001f)
        assertEquals(80f, t.maxVolumePercent, 0.001f)
        t.stop(240_000)
        assertEquals(120_000L, t.pending.sumOf { it.endTime!! - it.startTime })
        assertEquals(2, t.pending.size)
        t.stop(240_000)
        assertEquals(2, t.pending.size)
    }
    @Test fun pauseOverTwoMinutesEndsSession() {
        val t = HeadphoneUsageTracker()
        t.update(DeviceType.BLUETOOTH, true, 40f, 0, 1000)
        t.update(DeviceType.BLUETOOTH, false, 40f, 1000, 2000)
        t.update(DeviceType.BLUETOOTH, true, 40f, 121_001, 122_001)
        assertEquals(0L, t.durationMs)
        assertEquals(1, t.pending.size)
    }
    @Test fun unplugImmediatelyClosesAndRepeatedEventsDoNotDuplicate() {
        val t = HeadphoneUsageTracker()
        t.update(DeviceType.WIRED, true, 30f, 0, 1000)
        t.update(DeviceType.WIRED, true, 60f, 1000, 2000)
        t.update(null, true, 80f, 3000, 4000)
        assertEquals(1, t.pending.size)
        assertEquals(50f, t.pending.first().avgVolumePercent, 0.001f)
        assertEquals(60f, t.pending.first().maxVolumePercent, 0.001f)
        assertEquals(3000L, t.pending.first().endTime!! - t.pending.first().startTime)
        t.update(null, true, 80f, 4000, 5000)
        assertEquals(1, t.pending.size)
    }
    @Test fun monotonicTimeIgnoresWallClockJump() {
        val t = HeadphoneUsageTracker()
        t.update(DeviceType.WIRED, true, 50f, 10, 1000)
        t.update(DeviceType.WIRED, true, 50f, 1010, -100_000)
        t.stop(2010)
        assertEquals(2000L, t.pending.single().endTime!! - t.pending.single().startTime)
    }
    @Test fun clipsDayWeekAndIncludesLiveOnce() {
        val old = HeadphoneUsage(startTime = 0, endTime = 100, avgVolumePercent = 10f,
            maxVolumePercent = 10f, deviceType = DeviceType.WIRED)
        val live = old.copy(startTime = 120, endTime = 200)
        assertEquals(30L, clippedDuration(listOf(old), 70, 200))
        assertEquals(80L, clippedDuration(listOf(old, live), 70, 170))
        assertEquals(0L, clippedDuration(listOf(old), 100, 200))
    }
}
