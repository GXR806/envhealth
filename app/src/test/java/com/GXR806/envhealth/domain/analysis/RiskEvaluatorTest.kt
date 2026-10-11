package com.GXR806.envhealth.domain.analysis

import org.junit.Assert.*
import org.junit.Test

class RiskEvaluatorTest {
    private val evaluator = RiskEvaluator()
    private val hour = 3_600_000L
    @Test fun exactThresholdsAreSafe() {
        val result = evaluator.evaluate(hour, 60f, 40 * hour)
        assertEquals(HeadphoneRisk.SAFE, result.level)
        assertEquals(listOf("暂未触发提醒，仍建议定时休息"), result.reasons)
    }
    @Test fun volumeStrictlyOverSixtyIsLow() {
        assertEquals(HeadphoneRisk.LOW, evaluator.evaluate(0, 60.01f, 0).level)
        assertEquals(HeadphoneRisk.LOW, evaluator.evaluate(hour, 80f, 40 * hour).level)
    }
    @Test fun longQuietListeningIsMedium() {
        val result = evaluator.evaluate(hour + 1, 60f, 0)
        assertEquals(HeadphoneRisk.MEDIUM, result.level)
        assertEquals(listOf("本次累计收听超过 60 分钟，建议休息"), result.reasons)
    }
    @Test fun durationAndVolumeCombinationIsHighWithAllReasons() {
        val result = evaluator.evaluate(hour + 1, 60.01f, 0)
        assertEquals(HeadphoneRisk.HIGH, result.level)
        assertEquals(3, result.reasons.size)
        assertTrue(result.reasons.contains("长时间收听且当前音量超过 60%"))
    }
    @Test fun overEightyAloneIsHigh() {
        val result = evaluator.evaluate(0, 80.01f, 0)
        assertEquals(HeadphoneRisk.HIGH, result.level)
        assertEquals(2, result.reasons.size)
    }
    @Test fun weekThresholdIsStrict() {
        val result = evaluator.evaluate(0, 0f, 40 * hour + 1)
        assertEquals(HeadphoneRisk.HIGH, result.level)
        assertEquals(listOf("近 7 天累计超过 40 小时（产品提醒阈值）"), result.reasons)
    }
    @Test fun highPrecedenceDoesNotDiscardLowerRiskReasons() {
        val result = evaluator.evaluate(hour + 1, 81f, 40 * hour + 1)
        assertEquals(HeadphoneRisk.HIGH, result.level)
        assertEquals(5, result.reasons.size)
        assertEquals(5, result.reasons.distinct().size)
    }
}
