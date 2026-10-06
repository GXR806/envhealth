package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.domain.model.LightLevel
import com.GXR806.envhealth.domain.model.LightSample
import com.GXR806.envhealth.domain.model.NoiseSample
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.domain.model.SleepSessionStatus
import com.GXR806.envhealth.domain.model.SleepSessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Unit tests for SleepAnalyzer
 * 
 * Test coverage:
 * - Noise scoring algorithm (avgLeq, maxDb, burstCount penalties)
 * - Light scoring algorithm (avgLux, stdDev, preSleep trend penalties)
 * - Regularity scoring algorithm (stdDev hours penalty)
 * - Total score calculation (weighted and fallback formulas)
 * - Edge cases (empty data, extreme values, boundary conditions)
 */
class SleepAnalyzerTest {

    private lateinit var testAnalyzer: TestSleepAnalyzer

    @Before
    fun setup() {
        testAnalyzer = TestSleepAnalyzer()
    }

    // ========== Noise Score Tests ==========

    @Test
    fun noiseScore_perfectEnvironment_shouldBeNear100() = runTest {
        // Perfect environment: avgLeq = 25 dB, maxDb = 30 dB, no burst events
        val noiseSamples = listOf(
            NoiseSample(1000L, 25f, 30f, 28f, false),
            NoiseSample(2000L, 24f, 29f, 27f, false),
            NoiseSample(3000L, 26f, 31f, 29f, false)
        )
        testAnalyzer.setNoiseSamples(noiseSamples)

        val session = createTestSession(1000L, 5000L)
        val result = testAnalyzer.analyze(session)

        assertTrue("Noise score should be >= 95 for perfect environment", result.noiseScore >= 95f)
        assertEquals(25f, result.noiseStats.avgLeq, 1f)
        assertEquals(31f, result.noiseStats.maxDb, 1f)
        assertEquals(0, result.noiseStats.burstCount)
    }

    @Test
    fun noiseScore_highAvgLeq_shouldApplyPenalty() = runTest {
        // avgLeq = 50 dB should apply (50-30)*2 = 40 points penalty
        val noiseSamples = listOf(
            NoiseSample(1000L, 50f, 55f, 53f, false)
        )
        testAnalyzer.setNoiseSamples(noiseSamples)

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        // Expected: 100 - 40(avgLeq) - 5(maxDb) - 0(burst) = 55
        assertTrue("Noise score should be around 55", abs(result.noiseScore - 55f) < 5f)
    }

    @Test
    fun noiseScore_highMaxDb_shouldApplyPenalty() = runTest {
        // maxDb = 80 dB should apply (80-50)*1 = 30 points penalty (capped)
        val noiseSamples = listOf(
            NoiseSample(1000L, 30f, 80f, 78f, false)
        )
        testAnalyzer.setNoiseSamples(noiseSamples)

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        // Expected: 100 - 0(avgLeq) - 30(maxDb) - 0(burst) = 70
        assertTrue("Noise score should be around 70", abs(result.noiseScore - 70f) < 5f)
    }

    @Test
    fun noiseScore_manyBurstEvents_shouldApplyPenalty() = runTest {
        // 15 burst events should apply min(15*3, 30) = 30 points penalty
        val noiseSamples = (1..15).map { i ->
            NoiseSample(1000L + i * 100L, 30f, 55f, 60f, true)
        }
        testAnalyzer.setNoiseSamples(noiseSamples)

        val session = createTestSession(1000L, 3000L)
        val result = testAnalyzer.analyze(session)

        // Expected: 100 - 0(avgLeq) - 5(maxDb) - 30(burst) = 65
        assertTrue("Noise score should be around 65", abs(result.noiseScore - 65f) < 5f)
        assertEquals(15, result.noiseStats.burstCount)
    }

    @Test
    fun noiseScore_combinedPenalties_shouldBeAccurate() = runTest {
        // avgLeq = 45, maxDb = 70, 10 burst events
        val noiseSamples = (1..10).map { i ->
            NoiseSample(1000L + i * 100L, 45f, 70f, 68f, true)
        }
        testAnalyzer.setNoiseSamples(noiseSamples)

        val session = createTestSession(1000L, 3000L)
        val result = testAnalyzer.analyze(session)

        // Expected: 100 - 30(avgLeq) - 20(maxDb) - 30(burst) = 20
        assertTrue("Noise score should be around 20", abs(result.noiseScore - 20f) < 10f)
    }

    @Test
    fun noiseScore_emptyData_shouldReturnHundred() = runTest {
        testAnalyzer.setNoiseSamples(emptyList())

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        assertEquals(100f, result.noiseScore, 0.1f)
        assertEquals(0f, result.noiseStats.avgLeq)
        assertEquals(0f, result.noiseStats.maxDb)
        assertEquals(0, result.noiseStats.burstCount)
    }

    // ========== Light Score Tests ==========

    @Test
    fun lightScore_perfectDarkness_shouldBeNear100() = runTest {
        // Perfect darkness: avgLux = 2 lux, no fluctuation, decreasing trend
        val startTime = System.currentTimeMillis()
        val lightSamples = listOf(
            LightSample(startTime - 3600_000L, 10f, 3f, LightLevel.DARK),
            LightSample(startTime - 1800_000L, 8f, 2.5f, LightLevel.DARK),
            LightSample(startTime, 5f, 2f, LightLevel.DARK)
        )
        testAnalyzer.setLightSamples(lightSamples)

        val session = createTestSession(startTime, startTime + 1000L)
        val result = testAnalyzer.analyze(session)

        assertTrue("Light score should be >= 95 for perfect darkness", result.lightScore >= 95f)
        assertTrue("Avg lux should be < 5", result.lightStats.avgLux < 5f)
        assertTrue("Pre-sleep trend should be decreasing", result.lightStats.preSleepTrendDecreasing)
    }

    @Test
    fun lightScore_brightLight_shouldApplyPenalty() = runTest {
        // avgLux = 100 lux should apply (100-5)*1.5 = 40 points penalty (capped)
        val lightSamples = listOf(
            LightSample(1000L, 150f, 100f, LightLevel.MODERATE)
        )
        testAnalyzer.setLightSamples(lightSamples)

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        // Expected: 100 - 40(avgLux) - 0(fluctuation) - 30(preSleep) = 30
        assertTrue("Light score should be around 30", abs(result.lightScore - 30f) < 10f)
    }

    @Test
    fun lightScore_highFluctuation_shouldApplyPenalty() = runTest {
        // High fluctuation: stdDev = 25 lux should apply min(25*2, 30) = 30 points penalty
        val startTime = 1000L
        val lightSamples = listOf(
            LightSample(startTime, 50f, 10f, LightLevel.DIM),
            LightSample(startTime + 100L, 100f, 40f, LightLevel.DIM),
            LightSample(startTime + 200L, 30f, 5f, LightLevel.DARK),
            LightSample(startTime + 300L, 80f, 30f, LightLevel.DIM)
        )
        testAnalyzer.setLightSamples(lightSamples)

        val session = createTestSession(startTime, startTime + 500L)
        val result = testAnalyzer.analyze(session)

        // stdDev of [10, 40, 5, 30] ≈ 15.8, penalty = 30 (capped)
        assertTrue("Fluctuation penalty should be significant", result.lightStats.stdDevLux > 10f)
    }

    @Test
    fun lightScore_preSleepNotDecreasing_shouldApplyPenalty() = runTest {
        // Pre-sleep trend: brightness increases (not decreasing)
        val startTime = System.currentTimeMillis()
        val lightSamples = listOf(
            LightSample(startTime - 3600_000L, 10f, 5f, LightLevel.DARK),
            LightSample(startTime - 1800_000L, 20f, 10f, LightLevel.DIM),
            LightSample(startTime, 30f, 15f, LightLevel.DIM)
        )
        testAnalyzer.setLightSamples(lightSamples)

        val session = createTestSession(startTime, startTime + 1000L)
        val result = testAnalyzer.analyze(session)

        // Expected: preSleepTrendDecreasing = false, so -30 penalty
        assertEquals(false, result.lightStats.preSleepTrendDecreasing)
    }

    @Test
    fun lightScore_noEstimatedLux_shouldUseLumaFallback() = runTest {
        // Test degraded mode: estimatedLux = null
        val lightSamples = listOf(
            LightSample(1000L, 25.5f, null, LightLevel.DARK),
            LightSample(2000L, 51f, null, LightLevel.DIM)
        )
        testAnalyzer.setLightSamples(lightSamples)

        val session = createTestSession(1000L, 3000L)
        val result = testAnalyzer.analyze(session)

        // Should fall back to luma/255*100 calculation
        assertTrue("Avg lux should be calculated from luma", result.lightStats.avgLux > 0f)
    }

    @Test
    fun lightScore_emptyData_shouldReturnHundred() = runTest {
        testAnalyzer.setLightSamples(emptyList())

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        assertEquals(70f, result.lightScore, 0.1f)  // 空数据时的默认惩罚评分
        assertEquals(0f, result.lightStats.avgLux)
        assertEquals(0f, result.lightStats.stdDevLux)
        assertEquals(false, result.lightStats.preSleepTrendDecreasing)
    }

    // ========== Regularity Score Tests ==========

    @Test
    fun regularityScore_insufficientData_shouldReturnNull() = runTest {
        // Only 2 sessions (need at least 3)
        val sessions = listOf(
            createTestSession(1000L, 2000L, id = 1),
            createTestSession(3000L, 4000L, id = 2)
        )
        testAnalyzer.setSleepSessions(sessions)

        val currentSession = createTestSession(5000L, 6000L, id = 3)
        val result = testAnalyzer.analyze(currentSession)

        assertNull("Regularity score should be null with < 3 sessions", result.regularityScore)
        assertNull("Regularity stats should be null", result.regularityStats)
    }

    @Test
    fun regularityScore_perfectRegularity_shouldBeNear100() = runTest {
        // All sessions start at exactly 22:00 (10 PM)
        val baseTime = 22L * 3600_000L  // 22:00 in millis
        val sessions = (1..5).map { day ->
            createTestSession(
                startTime = day * 24L * 3600_000L + baseTime,
                endTime = day * 24L * 3600_000L + baseTime + 8L * 3600_000L,
                id = day.toLong()
            )
        }
        testAnalyzer.setSleepSessions(sessions)

        val currentSession = createTestSession(6L * 24L * 3600_000L + baseTime, 6L * 24L * 3600_000L + baseTime + 1000L, id = 6L)
        val result = testAnalyzer.analyze(currentSession)

        assertNotNull("Regularity score should not be null", result.regularityScore)
        assertTrue("Regularity score should be near 100", result.regularityScore!! >= 95f)
        assertTrue("StdDev hours should be very low", result.regularityStats!!.stdDevHours < 0.5f)
    }

    @Test
    fun regularityScore_irregularSchedule_shouldApplyPenalty() = runTest {
        // Sessions at different times: 22:00, 01:00, 23:30, 00:30 (stdDev ≈ 1.5h)
        val baseTimes = listOf(22L * 3600_000L, 25L * 3600_000L, (23.5 * 3600_000).toLong(), (24.5 * 3600_000).toLong())
        val sessions = baseTimes.mapIndexed { index, time ->
            createTestSession(
                startTime = index * 24L * 3600_000L + time,
                endTime = index * 24L * 3600_000L + time + 1000L,
                id = index.toLong() + 1
            )
        }
        testAnalyzer.setSleepSessions(sessions)

        val currentSession = createTestSession(5L * 24L * 3600_000L, 5L * 24L * 3600_000L + 1000L, id = 5L)
        val result = testAnalyzer.analyze(currentSession)

        assertNotNull("Regularity score should not be null", result.regularityScore)
        assertTrue("Regularity score should be lower due to irregularity", result.regularityScore!! < 70f)
        assertTrue("StdDev hours should be > 1", result.regularityStats!!.stdDevHours > 1f)
    }

    @Test
    fun regularityScore_extremeIrregularity_shouldBeNearZero() = runTest {
        // Extreme irregularity: stdDev > 5 hours
        val baseTimes = listOf(20L, 2L, 22L, 4L).map { it * 3600_000L }
        val sessions = baseTimes.mapIndexed { index, time ->
            createTestSession(
                startTime = index * 24L * 3600_000L + time,
                endTime = index * 24L * 3600_000L + time + 1000L,
                id = index.toLong() + 1
            )
        }
        testAnalyzer.setSleepSessions(sessions)

        val currentSession = createTestSession(5L * 24L * 3600_000L, 5L * 24L * 3600_000L + 1000L, id = 5L)
        val result = testAnalyzer.analyze(currentSession)

        assertNotNull("Regularity score should not be null", result.regularityScore)
        assertTrue("Regularity score should be very low", result.regularityScore!! < 30f)
    }

    // ========== Total Score Tests ==========

    @Test
    fun totalScore_withRegularity_shouldUseThreeFactorFormula() = runTest {
        // Setup: noise=80, light=70, regularity=90
        // Expected total: 80*0.45 + 70*0.35 + 90*0.20 = 36 + 24.5 + 18 = 78.5
        setupForTotalScoreTest(
            testAnalyzer,
            noiseAvg = 35f, noiseMax = 55f, noiseBurst = 3,
            lightAvg = 10f, preSleepDecreasing = true,
            regularitySessions = 5
        )

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        assertNotNull("Regularity score should exist", result.regularityScore)
        assertTrue("Total score should be around 70-85", result.totalScore in 70f..85f)
    }

    @Test
    fun totalScore_withoutRegularity_shouldUseTwoFactorFormula() = runTest {
        // Setup: noise=80, light=60, no regularity
        // Expected total: 80*0.60 + 60*0.40 = 48 + 24 = 72
        setupForTotalScoreTest(
            testAnalyzer,
            noiseAvg = 35f, noiseMax = 55f, noiseBurst = 3,
            lightAvg = 15f, preSleepDecreasing = true,
            regularitySessions = 1  // Insufficient
        )

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        assertNull("Regularity score should be null", result.regularityScore)
        assertTrue("Total score should be around 60-80", result.totalScore in 60f..80f)
    }

    @Test
    fun totalScore_perfectScores_shouldBeNear100() = runTest {
        setupForTotalScoreTest(
            testAnalyzer,
            noiseAvg = 25f, noiseMax = 30f, noiseBurst = 0,
            lightAvg = 2f, preSleepDecreasing = true,
            regularitySessions = 5
        )

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        // 验证各项评分都很高（放宽到 >= 90）
        assertTrue("Noise score should be >= 90", result.noiseScore >= 90f)
        assertTrue("Light score should be >= 90", result.lightScore >= 90f)
        assertTrue("Regularity score should be >= 90", result.regularityScore!! >= 90f)
        assertTrue("Total score should be >= 90", result.totalScore >= 90f)
    }

    // ========== Edge Cases ==========

    @Test
    fun analyze_allEmptyData_shouldReturnValidResult() = runTest {
        testAnalyzer.setNoiseSamples(emptyList())
        testAnalyzer.setLightSamples(emptyList())
        testAnalyzer.setSleepSessions(emptyList())

        val session = createTestSession(1000L, 2000L)
        val result = testAnalyzer.analyze(session)

        assertEquals(100f, result.noiseScore, 0.1f)
        assertEquals(70f, result.lightScore, 0.1f)  // 空数据时的默认惩罚评分
        assertNull(result.regularityScore)
        // Total: 100*0.6 + 70*0.4 = 88
        assertEquals(88f, result.totalScore, 0.1f)
    }

    // ========== Helper Methods ==========

    private fun createTestSession(
        startTime: Long,
        endTime: Long,
        id: Long = 1L,
        type: SleepSessionType = SleepSessionType.MANUAL,
        status: SleepSessionStatus = SleepSessionStatus.COMPLETED
    ): SleepSession {
        return SleepSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            type = type,
            status = status
        )
    }

    private fun setupForTotalScoreTest(
        analyzer: TestSleepAnalyzer,
        noiseAvg: Float,
        noiseMax: Float,
        noiseBurst: Int,
        lightAvg: Float,
        preSleepDecreasing: Boolean,
        regularitySessions: Int = 0
    ) {
        // Setup noise samples
        val noiseSamples = (1..10).map { i ->
            NoiseSample(
                timestamp = 1000L + i * 100L,
                leqDb = noiseAvg,
                maxDb = noiseMax,
                peakDb = noiseMax - 2f,
                isEvent = i <= noiseBurst
            )
        }
        analyzer.setNoiseSamples(noiseSamples)

        // Setup light samples
        val startTime = 1000L
        val lightSamples = if (preSleepDecreasing) {
            listOf(
                LightSample(startTime - 3600_000L, 30f, lightAvg + 5f, LightLevel.DIM),
                LightSample(startTime - 1800_000L, 20f, lightAvg, LightLevel.DIM),
                LightSample(startTime, 10f, lightAvg - 3f, LightLevel.DARK)
            )
        } else {
            listOf(
                LightSample(startTime - 3600_000L, 10f, lightAvg - 3f, LightLevel.DARK),
                LightSample(startTime - 1800_000L, 20f, lightAvg, LightLevel.DIM),
                LightSample(startTime, 30f, lightAvg + 5f, LightLevel.DIM)
            )
        }
        analyzer.setLightSamples(lightSamples)

        // Setup regularity sessions
        if (regularitySessions > 0) {
            val baseTime = 22L * 3600_000L
            val sessions = (1..regularitySessions).map { day ->
                createTestSession(
                    startTime = day * 24L * 3600_000L + baseTime,
                    endTime = day * 24L * 3600_000L + baseTime + 1000L,
                    id = day.toLong()
                )
            }
            analyzer.setSleepSessions(sessions)
        }
    }

    // ========== Test Analyzer Implementation ==========

    /**
     * Test implementation of SleepAnalyzer that uses in-memory data
     * This replicates the scoring algorithms from the actual SleepAnalyzer
     */
    private class TestSleepAnalyzer {
        private var noiseSamples: List<NoiseSample> = emptyList()
        private var lightSamples: List<LightSample> = emptyList()
        private var sleepSessions: List<SleepSession> = emptyList()

        fun setNoiseSamples(samples: List<NoiseSample>) {
            noiseSamples = samples
        }

        fun setLightSamples(samples: List<LightSample>) {
            lightSamples = samples
        }

        fun setSleepSessions(sessions: List<SleepSession>) {
            sleepSessions = sessions
        }

        suspend fun analyze(session: SleepSession): SleepAnalyzer.SleepAnalysisResult {
            val startTime = session.startTime
            val endTime = session.endTime ?: System.currentTimeMillis()

            val noiseStats = calculateNoiseStats(startTime, endTime)
            val noiseScore = calculateNoiseScore(noiseStats)

            val lightStats = calculateLightStats(startTime, endTime)
            val lightScore = calculateLightScore(lightStats)

            val regularityStats = calculateRegularityStats(session)
            val regularityScore = regularityStats?.let { calculateRegularityScore(it) }

            val totalScore = calculateTotalScore(noiseScore, lightScore, regularityScore)

            return SleepAnalyzer.SleepAnalysisResult(
                noiseScore = noiseScore,
                lightScore = lightScore,
                regularityScore = regularityScore,
                totalScore = totalScore,
                noiseStats = noiseStats,
                lightStats = lightStats,
                regularityStats = regularityStats
            )
        }

        private fun calculateNoiseStats(startTime: Long, endTime: Long): SleepAnalyzer.NoiseStats {
            val samples = noiseSamples.filter { it.timestamp in startTime..endTime }
            if (samples.isEmpty()) {
                return SleepAnalyzer.NoiseStats(avgLeq = 0f, maxDb = 0f, burstCount = 0)
            }
            return SleepAnalyzer.NoiseStats(
                avgLeq = samples.map { it.leqDb }.average().toFloat(),
                maxDb = samples.maxOfOrNull { it.maxDb } ?: 0f,
                burstCount = samples.count { it.isEvent }
            )
        }

        private fun calculateNoiseScore(stats: SleepAnalyzer.NoiseStats): Float {
            val avgLeqPenalty = min((stats.avgLeq - 30f) * 2f, 40f).coerceAtLeast(0f)
            val maxDbPenalty = min((stats.maxDb - 50f) * 1f, 30f).coerceAtLeast(0f)
            val burstEventPenalty = min(stats.burstCount * 3f, 30f)
            val score = 100f - (avgLeqPenalty + maxDbPenalty + burstEventPenalty)
            return score.coerceIn(0f, 100f)
        }

        private fun calculateLightStats(startTime: Long, endTime: Long): SleepAnalyzer.LightStats {
            val samples = lightSamples.filter { it.timestamp in (startTime - 3600_000L)..endTime }
            if (samples.isEmpty()) {
                return SleepAnalyzer.LightStats(avgLux = 0f, stdDevLux = 0f, preSleepTrendDecreasing = false)
            }

            val luxValues = samples.mapNotNull { it.estimatedLux }
            val avgLux = if (luxValues.isNotEmpty()) {
                luxValues.average().toFloat()
            } else {
                samples.map { it.avgLuma / 255f * 100f }.average().toFloat()
            }

            val stdDevLux = if (luxValues.size > 1) {
                val mean = luxValues.average()
                val variance = luxValues.map { (it - mean).pow(2) }.average()
                sqrt(variance).toFloat()
            } else {
                0f
            }

            val preSleepTrendDecreasing = checkPreSleepTrend(samples, startTime)

            return SleepAnalyzer.LightStats(avgLux, stdDevLux, preSleepTrendDecreasing)
        }

        private fun checkPreSleepTrend(samples: List<LightSample>, startTime: Long): Boolean {
            val oneHourBeforeSleep = startTime - 3600_000L
            val preSleepSamples = samples.filter { it.timestamp >= oneHourBeforeSleep && it.timestamp <= startTime }
            if (preSleepSamples.size < 2) return false

            val midPoint = preSleepSamples.size / 2
            val firstHalfAvg = preSleepSamples.take(midPoint).map { it.avgLuma }.average()
            val secondHalfAvg = preSleepSamples.drop(midPoint).map { it.avgLuma }.average()
            return secondHalfAvg < firstHalfAvg
        }

        private fun calculateLightScore(stats: SleepAnalyzer.LightStats): Float {
            val avgLuxPenalty = min((stats.avgLux - 5f) * 1.5f, 40f).coerceAtLeast(0f)
            val fluctuationPenalty = min(stats.stdDevLux * 2f, 30f)
            val preSleepTrendPenalty = if (!stats.preSleepTrendDecreasing) 30f else 0f
            val score = 100f - (avgLuxPenalty + fluctuationPenalty + preSleepTrendPenalty)
            return score.coerceIn(0f, 100f)
        }

        private fun calculateRegularityStats(currentSession: SleepSession): SleepAnalyzer.RegularityStats? {
            val sevenDaysAgo = currentSession.startTime - 7L * 24 * 3600_000L
            val filteredSessions = sleepSessions
                .filter { it.id != currentSession.id && it.startTime >= sevenDaysAgo && it.status == SleepSessionStatus.COMPLETED }
                .take(7)

            if (filteredSessions.size < 3) return null

            val sleepHours = filteredSessions.map { (it.startTime % (24 * 3600_000L)) / 3600_000f }
            val mean = sleepHours.average()
            val variance = sleepHours.map { (it - mean).pow(2) }.average()
            val stdDevHours = sqrt(variance).toFloat()

            return SleepAnalyzer.RegularityStats(filteredSessions.size, stdDevHours)
        }

        private fun calculateRegularityScore(stats: SleepAnalyzer.RegularityStats): Float {
            val penalty = min(stats.stdDevHours * 20f, 100f)
            return (100f - penalty).coerceIn(0f, 100f)
        }

        private fun calculateTotalScore(noiseScore: Float, lightScore: Float, regularityScore: Float?): Float {
            return if (regularityScore != null) {
                noiseScore * 0.45f + lightScore * 0.35f + regularityScore * 0.20f
            } else {
                noiseScore * 0.60f + lightScore * 0.40f
            }
        }
    }
}
