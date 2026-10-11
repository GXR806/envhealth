package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.repository.LightRepository
import com.GXR806.envhealth.data.repository.NoiseRepository
import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production analyzer, repositories and entity conversion; no copied scoring code. */
class SleepAnalyzerTest {
    private val noiseDao = ScoringNoiseDao()
    private val lightDao = ScoringLightDao()
    private val sessionDao = ScoringSessionDao()
    private val noise = NoiseRepository(noiseDao)
    private val light = LightRepository(lightDao)
    private val sleep = SleepRepository(sessionDao, ScoringReportDao())
    private val analyzer = SleepAnalyzer(noise, light, sleep)
    private val day = 86_400_000L
    private val start = 10 * day
    private fun session(id: Long = 99, time: Long = start, end: Long? = time + 60_000) =
        SleepSession(id, time, end, SleepSessionType.MANUAL, SleepSessionStatus.COMPLETED)
    private suspend fun noise(leq: Float, max: Float, bursts: Int = 0, time: Long = start) {
        noise.insertSample(NoiseSample(time, leq, max, max, bursts > 0, bursts))
    }
    private suspend fun light(lux: Float?, luma: Float = 10f, time: Long = start) {
        light.insertSample(LightSample(time, luma, lux, LightLevel.DARK))
    }
    private suspend fun history(vararg hours: Int) {
        hours.forEachIndexed { i, hour ->
            sleep.insertSession(session(i.toLong() + 1, start - (i + 1) * day + hour * 3_600_000L))
        }
    }

    @Test fun emptySamples_haveProductionDefaultsAndNullableRegularity() = runTest {
        val result = analyzer.analyze(session())
        assertEquals(100f, result.noiseScore, 0f)
        assertEquals(88f, result.lightScore, 0f)
        assertEquals(95.2f, result.totalScore, 0.001f)
        assertEquals(0, result.noiseStats.burstCount)
        assertEquals(0f, result.lightStats.stdDevLux, 0f)
        assertFalse(result.lightStats.preSleepTrendDecreasing)
        assertNull(result.regularityScore)
        assertNull(result.regularityStats)
    }

    @Test fun quietNoise_hasNoNegativePenalty() = runTest {
        noise(10f, 20f)
        assertEquals(100f, analyzer.analyze(session()).noiseScore, 0f)
    }

    @Test fun noiseAverageAndPeaks_useCurrentTwentyAndThirtyFiveBaselines() = runTest {
        noise(24f, 39f)
        noise(26f, 40f)
        val result = analyzer.analyze(session())
        assertEquals(25f, result.noiseStats.avgLeq, 0f)
        assertEquals(40f, result.noiseStats.maxDb, 0f)
        // 100 - 15 average penalty - 7.5 peak penalty
        assertEquals(77.5f, result.noiseScore, 0f)
    }

    @Test fun burstCounts_arePersistedCountsNotEventBooleans() = runTest {
        noise(20f, 35f, 2)
        noise(20f, 35f, 1)
        val result = analyzer.analyze(session())
        assertEquals(3, result.noiseStats.burstCount)
        assertEquals(88f, result.noiseScore, 0f)
    }

    @Test fun allNoisePenalties_capAtTheirProductionWeights() = runTest {
        noise(120f, 140f, 100)
        assertEquals(0f, analyzer.analyze(session()).noiseScore, 0f)
    }

    @Test fun daoTimeRange_excludesOutOfRangeFeatures() = runTest {
        noise(120f, 140f, 100, start - 1)
        noise(20f, 35f, time = start + 60_000)
        light(1000f, time = start + 60_001)
        val result = analyzer.analyze(session())
        assertEquals(100f, result.noiseScore, 0f)
        assertEquals(88f, result.lightScore, 0f)
        assertEquals(start to start + 60_000, noiseDao.lastRange)
        assertEquals(start to start + 60_000, lightDao.lastRange)
    }

    @Test fun oneLightSample_hasNoVarianceButMissingTrendPenalty() = runTest {
        light(4f)
        val result = analyzer.analyze(session())
        assertEquals(4f, result.lightStats.avgLux, 0f)
        assertEquals(0f, result.lightStats.stdDevLux, 0f)
        assertEquals(75f, result.lightScore, 0f)
        assertEquals(90f, result.totalScore, 0.001f)
    }

    @Test fun zeroLux_preservesExistingEightyEightDefault() = runTest {
        light(0f)
        assertEquals(88f, analyzer.analyze(session()).lightScore, 0f)
    }

    @Test fun missingLux_usesRelativeLumaFallback() = runTest {
        light(null, 25.5f)
        val result = analyzer.analyze(session())
        assertEquals(10f, result.lightStats.avgLux, 0.001f)
        assertEquals(60f, result.lightScore, 0.001f)
    }

    @Test fun mixedLux_averagesOnlyNonNullValues() = runTest {
        light(null, 255f)
        light(2f, 10f, start + 1)
        val result = analyzer.analyze(session())
        assertEquals(2f, result.lightStats.avgLux, 0f)
        assertEquals(80f, result.lightScore, 0f)
    }

    @Test fun fluctuatingBrightLight_capsAllThreePenalties() = runTest {
        light(20f, time = start + 1)
        light(100f, time = start + 2)
        val result = analyzer.analyze(session())
        assertEquals(60f, result.lightStats.avgLux, 0f)
        assertEquals(40f, result.lightStats.stdDevLux, 0f)
        assertEquals(0f, result.lightScore, 0f)
    }

    @Test fun preSleepTrend_usesProductionRangeAndLumaComparison() = runTest {
        // Existing DAO range begins at startTime; duplicate timestamps make both samples
        // available to the production trend branch. Do not silently change its contract.
        light(2f, 20f)
        light(2f, 10f)
        val result = analyzer.analyze(session())
        assertTrue(result.lightStats.preSleepTrendDecreasing)
        assertEquals(100f, result.lightScore, 0f)
    }

    @Test fun increasingPreSleepLuma_keepsTrendPenalty() = runTest {
        light(2f, 10f)
        light(2f, 20f)
        assertFalse(analyzer.analyze(session()).lightStats.preSleepTrendDecreasing)
        assertEquals(80f, analyzer.analyze(session()).lightScore, 0f)
    }

    @Test fun twoCompletedSessions_areInsufficient() = runTest {
        history(0, 0)
        assertNull(analyzer.analyze(session()).regularityScore)
    }

    @Test fun regularSessions_enableThreeWayWeights() = runTest {
        history(0, 0, 0)
        noise(25f, 35f)
        light(4f)
        val result = analyzer.analyze(session())
        assertEquals(100f, result.regularityScore!!, 0f)
        assertEquals(3, result.regularityStats!!.sessionCount)
        assertEquals(0f, result.regularityStats.stdDevHours, 0f)
        // 85*0.45 + 75*0.35 + 100*0.20
        assertEquals(84.5f, result.totalScore, 0.001f)
        assertEquals(7, sessionDao.lastLimit)
    }

    @Test fun irregularSessions_haveDeterministicVariancePenalty() = runTest {
        history(0, 3, 6)
        val result = analyzer.analyze(session())
        assertEquals(2.4494898f, result.regularityStats!!.stdDevHours, 0.0001f)
        assertEquals(51.010204f, result.regularityScore!!, 0.001f)
        assertEquals(86.00204f, result.totalScore, 0.001f)
    }

    @Test fun extremeRegularity_capsAtZero() = runTest {
        history(0, 12, 23)
        assertEquals(0f, analyzer.analyze(session()).regularityScore!!, 0f)
    }

    @Test fun currentSessionAndOldHistory_doNotCountTowardRegularity() = runTest {
        history(0, 0)
        sleep.insertSession(session())
        sleep.insertSession(session(4, start - 7 * day - 1))
        assertNull(analyzer.analyze(session()).regularityScore)
    }

    @Test fun exactlySevenDaysOldSession_counts() = runTest {
        history(0, 0)
        sleep.insertSession(session(3, start - 7 * day))
        assertEquals(100f, analyzer.analyze(session()).regularityScore!!, 0f)
    }

    @Test fun ongoingSession_usesCurrentWallClockAsEnd() = runTest {
        val before = System.currentTimeMillis()
        analyzer.analyze(session(end = null))
        val after = System.currentTimeMillis()
        assertTrue(noiseDao.lastRange!!.second in before..after)
    }

    @Test fun repositoryFailure_isPropagatedRatherThanFakeScore() = runTest {
        val failure = IllegalStateException("读取失败")
        noiseDao.failure = failure
        try {
            analyzer.analyze(session())
            fail("Expected repository failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
    }

    @Test fun cancellation_isRethrown() = runTest {
        val failure = CancellationException("cancel")
        noiseDao.failure = failure
        try {
            analyzer.analyze(session())
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(failure, actual)
        }
    }
}
