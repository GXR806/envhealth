package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.domain.analysis.ReminderPolicy.Action
import com.GXR806.envhealth.domain.analysis.ReminderPolicy.Baselines
import com.GXR806.envhealth.domain.analysis.ReminderPolicy.Event
import com.GXR806.envhealth.domain.analysis.ReminderPolicy.Feedback
import com.GXR806.envhealth.domain.analysis.ReminderPolicy.LiveRisk
import com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val day = ReminderPolicy.DAY_MS
    @Test fun throttleBoundariesAndBackwardClock() {
        assertTrue(ReminderPolicy.maySend(day, null, null))
        assertFalse(ReminderPolicy.maySend(day - 1, 0, null))
        assertTrue(ReminderPolicy.maySend(day, 0, null))
        assertFalse(ReminderPolicy.maySend(99, 100, null))
        assertFalse(ReminderPolicy.maySend(7 * day - 1, 0, 7 * day))
        assertTrue(ReminderPolicy.maySend(7 * day, 0, 7 * day))
    }
    @Test fun quietAcrossMidnightAndEqualTimes() {
        assertTrue(ReminderPolicy.isQuiet(22 * 60, 22 * 60, 7 * 60))
        assertTrue(ReminderPolicy.isQuiet(0, 22 * 60, 7 * 60))
        assertFalse(ReminderPolicy.isQuiet(7 * 60, 22 * 60, 7 * 60))
        assertFalse(ReminderPolicy.isQuiet(21 * 60, 22 * 60, 7 * 60))
        assertTrue(ReminderPolicy.isQuiet(12 * 60, 0, 0))
        assertTrue(ReminderPolicy.isQuiet(600, 600, 800))
        assertFalse(ReminderPolicy.isQuiet(800, 600, 800))
    }
    private fun event(id: String, response: Boolean = false) = Event(id, Type.VOLUME, 0, false, 0,
        if (response) Action.LOWER_VOLUME else null, if (response) 1 else null, response)
    @Test fun responseWindowMinimumSampleAndBaselineVersion() {
        val list = (1..5).map { event("$it", it <= 3) }
        assertEquals(0, ReminderPolicy.feedback(list, Type.VOLUME, day - 1, 0).eligible)
        val result = ReminderPolicy.feedback(list, Type.VOLUME, day, 0)
        assertEquals(5, result.eligible)
        assertEquals(3, result.responded)
        assertEquals(-5, ReminderPolicy.adjustment(result))
        assertEquals(0, ReminderPolicy.adjustment(Feedback(4, 4)))
        assertEquals(0, ReminderPolicy.feedback(list, Type.VOLUME, day, 1).eligible)
        assertEquals(0, ReminderPolicy.feedback(list, Type.VOLUME, 31 * day, 0).eligible)
        assertEquals(5, ReminderPolicy.adjustment(Feedback(5, 0)))
        assertEquals(0, ReminderPolicy.adjustment(Feedback(5, 1)))
    }
    @Test fun ineffectiveAndLateActionsDoNotCount() {
        val list = listOf(event("a").copy(action = Action.LOWER_VOLUME, actionAt = 1, effective = false),
            event("b").copy(action = Action.LOWER_VOLUME, actionAt = day + 1, effective = true),
            event("c").copy(action = Action.MUTE, actionAt = 1, effective = true))
        assertEquals(0, ReminderPolicy.feedback(list, Type.VOLUME, day + 1, 0).responded)
    }
    @Test fun personalizationIsBoundedAndRelativeToManualBaseline() {
        val events = (1..5).map { event("$it") }
        val base = Baselines(70f, 80f, 90)
        val adjusted = ReminderPolicy.personalized(base, events, day, 0)
        assertEquals(80f, adjusted.volume, 0f)
        assertEquals(70f, adjusted.noise, 0f)
        assertEquals(90, adjusted.duration)
        assertEquals(60f, ReminderPolicy.personalized(Baselines(volume = 65f),
            (1..5).map { event("$it", true) }, day, 0).volume, 0f)
    }
    @Test fun staleReadingsDoNotTriggerCurrentRiskButWeekCan() {
        val stale = LiveRisk(90f, 0, 90f, 0, true, 100 * 60_000L, 41 * 60 * 60_000L)
        assertEquals(setOf(Type.WEEKLY), ReminderPolicy.risks(stale, Baselines(), day))
        val severe = stale.copy(volumeAt = day, noiseAt = day)
        assertTrue(ReminderPolicy.risks(severe, Baselines(90f, 80f, 90), day).contains(Type.VOLUME))
        assertFalse(ReminderPolicy.fresh(99, 100))
        assertTrue(ReminderPolicy.fresh(ReminderPolicy.FRESH_MS, 0))
    }
    @Test fun volumeNeverIncreasesAndUsesFloor() {
        assertEquals(9, ReminderPolicy.lowerVolumeTarget(14, 15, 60f))
        assertEquals(2, ReminderPolicy.lowerVolumeTarget(3, 15, 60f))
        assertEquals(7, ReminderPolicy.lowerVolumeTarget(14, 15, 50f))
        assertEquals(0, ReminderPolicy.lowerVolumeTarget(1, 15, 60f))
        assertNull(ReminderPolicy.lowerVolumeTarget(0, 15, 60f))
        assertNull(ReminderPolicy.lowerVolumeTarget(14, 15, Float.NaN))
    }
    @Test fun actionTypeReplayAndWindowValidation() {
        val event = event("a")
        assertTrue(ReminderPolicy.validAction(event, Type.VOLUME, day))
        assertFalse(ReminderPolicy.validAction(event, Type.NOISE, day))
        assertFalse(ReminderPolicy.validAction(event, Type.VOLUME, day + 1))
        assertFalse(ReminderPolicy.validAction(event.copy(action = Action.LATER), Type.VOLUME, 1))
        assertFalse(ReminderPolicy.validAction(event, Type.VOLUME, -1))
    }
    @Test fun conservativeRetentionAndDailyCleanup() {
        assertFalse(RetentionPolicy.cleanupDue(day - 1, 0))
        assertTrue(RetentionPolicy.cleanupDue(day, 0))
        assertFalse(RetentionPolicy.cleanupDue(99, 100))
        assertFalse(RetentionPolicy.completedExpired(null, 100 * day))
        assertFalse(RetentionPolicy.completedExpired(10 * day, 100 * day))
        assertTrue(RetentionPolicy.completedExpired(10 * day - 1, 100 * day))
        assertFalse(RetentionPolicy.sampleExpired(93 * day, 100 * day))
        assertTrue(RetentionPolicy.sampleExpired(93 * day - 1, 100 * day))
    }
}
