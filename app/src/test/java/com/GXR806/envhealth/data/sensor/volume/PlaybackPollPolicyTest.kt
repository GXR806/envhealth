package com.GXR806.envhealth.data.sensor.volume

import org.junit.Assert.*
import org.junit.Test

class PlaybackPollPolicyTest {
    @Test fun normalPollUsesExistingFiveSecondTick() {
        assertEquals(5_000L, PlaybackPollPolicy.INTERVAL_MS)
        assertFalse(PlaybackPollPolicy.shouldPoll(false, false, 100L, 5_099L))
        assertTrue(PlaybackPollPolicy.shouldPoll(false, false, 100L, 5_100L))
    }
    @Test fun actualVolumeChangeAndDeviceStartForceImmediately() {
        assertTrue(PlaybackPollPolicy.shouldPoll(false, true, 100L, 101L))
        assertTrue(PlaybackPollPolicy.shouldPoll(true, false, 100L, 101L))
        assertTrue(PlaybackPollPolicy.shouldPoll(false, false, Long.MIN_VALUE, 0L))
    }
    @Test fun unrelatedSettingsChangeDoesNotPollBeforeCooldown() {
        assertFalse(PlaybackPollPolicy.shouldPoll(false, false, 100L, 101L))
        assertFalse(PlaybackPollPolicy.shouldPoll(false, false, 100L, 100L))
    }
}
