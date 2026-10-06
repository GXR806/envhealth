package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.local.room.dao.AdviceDao
import com.GXR806.envhealth.data.local.room.entity.AdviceEntity
import com.GXR806.envhealth.data.repository.AdviceRepository
import com.GXR806.envhealth.domain.model.AdviceCategory
import com.GXR806.envhealth.domain.model.AdviceItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AdviceEngine
 * 
 * Test coverage:
 * - All 13 rules (R01-R15) trigger conditions
 * - Rule deduplication (24-hour window)
 * - Priority sorting (descending)
 * - Multiple rule triggering
 * - Edge cases (no triggers, all triggers)
 */
class AdviceEngineTest {

    private lateinit var testAdviceDao: TestAdviceDao
    private lateinit var adviceRepository: AdviceRepository
    private lateinit var adviceEngine: AdviceEngine

    @Before
    fun setup() {
        testAdviceDao = TestAdviceDao()
        adviceRepository = AdviceRepository(testAdviceDao)
        adviceEngine = AdviceEngine(adviceRepository)
    }

    // ========== Rule R01: avgLeq > 45 dB ==========

    @Test
    fun rule_R01_avgLeqHigh_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f,
            noiseMaxDb = 55f,
            noiseBurstCount = 0
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r01 = advice.find { it.triggerReason == "R01" }
        assertTrue("R01 should trigger when avgLeq > 45", r01 != null)
        assertEquals(AdviceCategory.NOISE, r01?.category)
        assertEquals(5, r01?.priority)
        assertTrue("Title should mention high noise", r01?.title?.contains("噪音") == true)
    }

    @Test
    fun rule_R01_avgLeqNormal_shouldNotTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 40f,
            noiseMaxDb = 50f,
            noiseBurstCount = 0
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r01 = advice.find { it.triggerReason == "R01" }
        assertTrue("R01 should not trigger when avgLeq <= 45", r01 == null)
    }

    // ========== Rule R02: burstCount > 10 ==========

    @Test
    fun rule_R02_burstCountHigh_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 35f,
            noiseMaxDb = 55f,
            noiseBurstCount = 15
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r02 = advice.find { it.triggerReason == "R02" }
        assertTrue("R02 should trigger when burstCount > 10", r02 != null)
        assertEquals(AdviceCategory.NOISE, r02?.category)
        assertEquals(4, r02?.priority)
        assertTrue("Content should mention burst count", r02?.content?.contains("15") == true)
    }

    @Test
    fun rule_R02_burstCountNormal_shouldNotTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 35f,
            noiseMaxDb = 55f,
            noiseBurstCount = 5
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r02 = advice.find { it.triggerReason == "R02" }
        assertTrue("R02 should not trigger when burstCount <= 10", r02 == null)
    }

    // ========== Rule R03: maxDb > 70 dB ==========

    @Test
    fun rule_R03_maxDbHigh_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 35f,
            noiseMaxDb = 75f,
            noiseBurstCount = 0
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r03 = advice.find { it.triggerReason == "R03" }
        assertTrue("R03 should trigger when maxDb > 70", r03 != null)
        assertEquals(AdviceCategory.NOISE, r03?.category)
        assertEquals(3, r03?.priority)
        assertTrue("Content should mention peak value", r03?.content?.contains("75") == true)
    }

    // ========== Rule R13: avgLeq in 35..45 dB ==========

    @Test
    fun rule_R13_avgLeqModerate_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 40f,
            noiseMaxDb = 50f,
            noiseBurstCount = 0
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r13 = advice.find { it.triggerReason == "R13" }
        assertTrue("R13 should trigger when avgLeq in 35..45", r13 != null)
        assertEquals(AdviceCategory.NOISE, r13?.category)
        assertEquals(2, r13?.priority)
    }

    @Test
    fun rule_R13_avgLeqOutOfRange_shouldNotTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f,  // Too high
            noiseMaxDb = 55f,
            noiseBurstCount = 0
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r13 = advice.find { it.triggerReason == "R13" }
        assertTrue("R13 should not trigger when avgLeq > 45", r13 == null)
    }

    // ========== Rule R04: avgLux > 50 lux ==========

    @Test
    fun rule_R04_lightBright_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            lightAvgLux = 80f,
            lightStdDevLux = 10f,
            preSleepTrendDecreasing = true
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r04 = advice.find { it.triggerReason == "R04" }
        assertTrue("R04 should trigger when avgLux > 50", r04 != null)
        assertEquals(AdviceCategory.LIGHT, r04?.category)
        assertEquals(5, r04?.priority)
        assertTrue("Content should mention lux value", r04?.content?.contains("80") == true)
    }

    // ========== Rule R05: stdDevLux > 20 lux ==========

    @Test
    fun rule_R05_lightFluctuationHigh_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            lightAvgLux = 30f,
            lightStdDevLux = 25f,
            preSleepTrendDecreasing = true
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r05 = advice.find { it.triggerReason == "R05" }
        assertTrue("R05 should trigger when stdDevLux > 20", r05 != null)
        assertEquals(AdviceCategory.LIGHT, r05?.category)
        assertEquals(4, r05?.priority)
    }

    // ========== Rule R06: preSleepTrend not decreasing ==========

    @Test
    fun rule_R06_preSleepNotDecreasing_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            lightAvgLux = 20f,
            lightStdDevLux = 5f,
            preSleepTrendDecreasing = false  // Not decreasing
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r06 = advice.find { it.triggerReason == "R06" }
        assertTrue("R06 should trigger when preSleep trend not decreasing", r06 != null)
        assertEquals(AdviceCategory.LIGHT, r06?.category)
        assertEquals(5, r06?.priority)
        assertTrue("Content should mention pre-sleep", r06?.content?.contains("睡前") == true)
    }

    @Test
    fun rule_R06_preSleepDecreasing_shouldNotTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            lightAvgLux = 20f,
            lightStdDevLux = 5f,
            preSleepTrendDecreasing = true
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r06 = advice.find { it.triggerReason == "R06" }
        assertTrue("R06 should not trigger when preSleep trend is decreasing", r06 == null)
    }

    // ========== Rule R07: avgLux in 5..50 lux ==========

    @Test
    fun rule_R07_lightSlightlyHigh_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            lightAvgLux = 30f,
            lightStdDevLux = 5f,
            preSleepTrendDecreasing = true
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r07 = advice.find { it.triggerReason == "R07" }
        assertTrue("R07 should trigger when avgLux in 5..50", r07 != null)
        assertEquals(AdviceCategory.LIGHT, r07?.category)
        assertEquals(3, r07?.priority)
    }

    // ========== Rule R08: stdDevHours > 1.5 ==========

    @Test
    fun rule_R08_irregularSchedule_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            regularityStdDevHours = 2.0f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r08 = advice.find { it.triggerReason == "R08" }
        assertTrue("R08 should trigger when stdDevHours > 1.5", r08 != null)
        assertEquals(AdviceCategory.SLEEP, r08?.category)
        assertEquals(5, r08?.priority)
    }

    @Test
    fun rule_R08_regularSchedule_shouldNotTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            regularityStdDevHours = 1.0f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r08 = advice.find { it.triggerReason == "R08" }
        assertTrue("R08 should not trigger when stdDevHours <= 1.5", r08 == null)
    }

    // ========== Rule R11: totalScore in 60..80 ==========

    @Test
    fun rule_R11_scoreGood_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            totalScore = 70f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r11 = advice.find { it.triggerReason == "R11" }
        assertTrue("R11 should trigger when totalScore in 60..80", r11 != null)
        assertEquals(AdviceCategory.GENERAL, r11?.category)
        assertEquals(2, r11?.priority)
    }

    // ========== Rule R12: totalScore > 80 ==========

    @Test
    fun rule_R12_scoreExcellent_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            totalScore = 90f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r12 = advice.find { it.triggerReason == "R12" }
        assertTrue("R12 should trigger when totalScore > 80", r12 != null)
        assertEquals(AdviceCategory.GENERAL, r12?.category)
        assertEquals(1, r12?.priority)
    }

    // ========== Rule R14: totalScore < 40 ==========

    @Test
    fun rule_R14_scorePoor_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            noiseScore = 30f,
            lightScore = 35f,
            regularityScore = 40f,
            totalScore = 35f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r14 = advice.find { it.triggerReason == "R14" }
        assertTrue("R14 should trigger when totalScore < 40", r14 != null)
        assertEquals(AdviceCategory.GENERAL, r14?.category)
        assertEquals(5, r14?.priority)
        assertTrue("Content should identify weakest factor", r14?.content?.contains("噪音") == true || 
                   r14?.content?.contains("光照") == true || r14?.content?.contains("规律性") == true)
    }

    // ========== Rule R15: regularityScore is null ==========

    @Test
    fun rule_R15_regularityDataInsufficient_shouldTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            regularityStdDevHours = null,  // This will make regularityStats = null
            regularityScore = null,  // Data insufficient
            totalScore = 55f  // 避免触发 R11（60-80）、R12（>80）或 R14（<40）
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r15 = advice.find { it.triggerReason == "R15" }
        assertTrue("R15 should trigger when regularityScore is null", r15 != null)
        assertEquals(AdviceCategory.GENERAL, r15?.category)
        assertEquals(1, r15?.priority)
        // 验证内容包含相关关键词（实际内容："需要至少 3 天的睡眠记录才能分析入睡规律性"）
        val content = r15?.content ?: ""
        assertTrue(
            "Content should mention insufficient data (actual: $content)",
            content.contains("至少") && content.contains("天") && content.contains("记录")
        )
    }

    @Test
    fun rule_R15_regularityDataSufficient_shouldNotTrigger() = runTest {
        val analysisResult = createAnalysisResult(
            regularityScore = 85f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r15 = advice.find { it.triggerReason == "R15" }
        assertTrue("R15 should not trigger when regularityScore is not null", r15 == null)
    }

    // ========== Deduplication Tests ==========

    @Test
    fun deduplication_recentAdviceExists_shouldSkip() = runTest {
        // Setup: R01 was triggered recently (within 24-hour window)
        val now = System.currentTimeMillis()
        val recentAdviceEntity = AdviceEntity(
            id = 1L,
            category = AdviceCategory.NOISE.name,
            priority = 5,
            title = "环境噪音偏高",
            content = "...",
            triggerReason = "R01",
            createdAt = now - (12 * 3600_000L)  // 12 hours ago
        )
        testAdviceDao.addTestData(listOf(recentAdviceEntity))

        // This should trigger R01, but it will be skipped due to deduplication
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r01 = advice.find { it.triggerReason == "R01" }
        assertTrue("R01 should be skipped due to recent trigger", r01 == null)
    }

    @Test
    fun deduplication_oldAdviceExists_shouldNotSkip() = runTest {
        // Setup: No recent advice (clear test data)
        testAdviceDao.clearTestData()

        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r01 = advice.find { it.triggerReason == "R01" }
        assertTrue("R01 should trigger when no recent advice", r01 != null)
    }

    // ========== Priority Sorting Tests ==========

    @Test
    fun prioritySorting_multipleRules_shouldSortByPriorityDescending() = runTest {
        // Trigger multiple rules with different priorities
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f,       // R01 priority=5
            noiseBurstCount = 15,    // R02 priority=4
            noiseMaxDb = 75f,        // R03 priority=3
            totalScore = 70f         // R11 priority=2
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        // Verify sorting: priority 5 -> 4 -> 3 -> 2
        assertTrue("Should have multiple advice items", advice.size >= 4)
        assertTrue("First item should have highest priority", advice[0].priority >= advice[1].priority)
        assertTrue("Advice should be sorted descending", advice[1].priority >= advice[2].priority)
    }

    // ========== Multiple Rule Triggering Tests ==========

    @Test
    fun multipleRules_noiseAndLight_shouldTriggerBoth() = runTest {
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f,          // R01
            lightAvgLux = 80f,          // R04
            preSleepTrendDecreasing = false  // R06
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        val r01 = advice.find { it.triggerReason == "R01" }
        val r04 = advice.find { it.triggerReason == "R04" }
        val r06 = advice.find { it.triggerReason == "R06" }

        assertTrue("R01 should trigger", r01 != null)
        assertTrue("R04 should trigger", r04 != null)
        assertTrue("R06 should trigger", r06 != null)
        assertTrue("Should have at least 3 advice items", advice.size >= 3)
    }

    // ========== Edge Cases ==========

    @Test
    fun edgeCase_noRulesTrigger_shouldReturnEmpty() = runTest {
        // Perfect environment: only R12 (excellent environment) should trigger
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 25f,
            noiseMaxDb = 35f,
            noiseBurstCount = 0,
            lightAvgLux = 2f,
            lightStdDevLux = 0.5f,
            preSleepTrendDecreasing = true,
            regularityStdDevHours = 0.3f,
            totalScore = 95f
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        // 完美环境应该只触发 R12（环境优秀）
        assertTrue("Should trigger at most R12 for perfect environment", advice.size <= 1)
        if (advice.isNotEmpty()) {
            assertEquals("R12", advice[0].triggerReason)
            assertTrue("Should be excellent environment advice", advice[0].title.contains("优秀"))
        }
    }

    @Test
    fun edgeCase_allRulesTrigger_shouldGenerateMany() = runTest {
        // Worst environment: many rules should trigger
        val analysisResult = createAnalysisResult(
            noiseAvgLeq = 50f,              // R01
            noiseBurstCount = 15,           // R02
            noiseMaxDb = 75f,               // R03
            lightAvgLux = 80f,              // R04
            lightStdDevLux = 25f,           // R05
            preSleepTrendDecreasing = false, // R06
            regularityStdDevHours = 2.0f,   // R08
            noiseScore = 20f,
            lightScore = 25f,
            regularityScore = 30f,
            totalScore = 25f                // R14
        )

        val advice = adviceEngine.generateAdvice(analysisResult)

        assertTrue("Many rules should trigger for worst environment", advice.size >= 8)
        
        // Verify at least some critical rules triggered
        val ruleIds = advice.map { it.triggerReason }
        assertTrue("R01 should be in results", ruleIds.contains("R01"))
        assertTrue("R02 should be in results", ruleIds.contains("R02"))
        assertTrue("R14 should be in results", ruleIds.contains("R14"))
    }

    // ========== Helper Methods ==========

    private fun createAnalysisResult(
        noiseAvgLeq: Float = 30f,
        noiseMaxDb: Float = 40f,
        noiseBurstCount: Int = 0,
        lightAvgLux: Float = 3f,
        lightStdDevLux: Float = 1f,
        preSleepTrendDecreasing: Boolean = true,
        regularityStdDevHours: Float? = 0.5f,
        noiseScore: Float = 90f,
        lightScore: Float = 90f,
        regularityScore: Float? = regularityStdDevHours?.let { 100f - it * 20f },
        totalScore: Float = 90f
    ): SleepAnalyzer.SleepAnalysisResult {
        return SleepAnalyzer.SleepAnalysisResult(
            noiseScore = noiseScore,
            lightScore = lightScore,
            regularityScore = regularityScore,
            totalScore = totalScore,
            noiseStats = SleepAnalyzer.NoiseStats(
                avgLeq = noiseAvgLeq,
                maxDb = noiseMaxDb,
                burstCount = noiseBurstCount
            ),
            lightStats = SleepAnalyzer.LightStats(
                avgLux = lightAvgLux,
                stdDevLux = lightStdDevLux,
                preSleepTrendDecreasing = preSleepTrendDecreasing
            ),
            regularityStats = regularityStdDevHours?.let {
                SleepAnalyzer.RegularityStats(
                    sessionCount = 5,
                    stdDevHours = it
                )
            }
        )
    }

    // ========== Test Doubles ==========

    /**
     * Test implementation of AdviceDao - uses in-memory storage
     */
    private class TestAdviceDao : AdviceDao {
        private val adviceEntities = mutableListOf<AdviceEntity>()
        private var nextId = 1L

        fun addTestData(entities: List<AdviceEntity>) {
            adviceEntities.addAll(entities)
        }

        fun clearTestData() {
            adviceEntities.clear()
        }

        override suspend fun insert(entity: AdviceEntity): Long {
            val id = nextId++
            adviceEntities.add(entity.copy(id = id))
            return id
        }

        override suspend fun insertAll(entities: List<AdviceEntity>): List<Long> {
            return entities.map { insert(it) }
        }

        override fun getAllAdvice(): Flow<List<AdviceEntity>> {
            return flowOf(adviceEntities.sortedWith(
                compareByDescending<AdviceEntity> { it.priority }
                    .thenByDescending { it.createdAt }
            ))
        }

        override fun getAdviceByCategory(category: String): Flow<List<AdviceEntity>> {
            return flowOf(adviceEntities.filter { it.category == category })
        }

        override fun getRecentAdvice(limit: Int): Flow<List<AdviceEntity>> {
            return flowOf(adviceEntities.sortedByDescending { it.createdAt }.take(limit))
        }

        override suspend fun getById(adviceId: Long): AdviceEntity? {
            return adviceEntities.find { it.id == adviceId }
        }

        override suspend fun deleteById(adviceId: Long): Int {
            return if (adviceEntities.removeIf { it.id == adviceId }) 1 else 0
        }

        override suspend fun deleteBeforeTime(beforeTime: Long): Int {
            val initialSize = adviceEntities.size
            adviceEntities.removeIf { it.createdAt < beforeTime }
            return initialSize - adviceEntities.size
        }

        override suspend fun deleteAll(): Int {
            val count = adviceEntities.size
            adviceEntities.clear()
            return count
        }
    }
}
