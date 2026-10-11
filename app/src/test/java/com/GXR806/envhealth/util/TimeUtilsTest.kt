package com.GXR806.envhealth.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {
    @Test fun epochIsUtc() {
        assertEquals("1970-01-01T00:00:00Z", TimeUtils.formatIso8601(0))
    }
    @Test fun positiveMillisPreserveFraction() {
        assertEquals("1970-01-01T00:00:01.234Z", TimeUtils.formatIso8601(1234))
    }
    @Test fun negativeMillisUsePreviousUtcDate() {
        assertEquals("1969-12-31T23:59:59.999Z", TimeUtils.formatIso8601(-1))
        assertEquals("1969-12-31T23:59:58.766Z", TimeUtils.formatIso8601(-1234))
    }
    @Test fun nowIsWithinWallClockBounds() {
        val before = System.currentTimeMillis()
        val actual = TimeUtils.nowMillis()
        val after = System.currentTimeMillis()
        assertTrue(actual in before..after)
    }
}
