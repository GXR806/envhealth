package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.sensor.audio.AWeightingFilter
import com.GXR806.envhealth.data.sensor.audio.NoiseFrame
import org.junit.Assert.*
import org.junit.Test

class AudioOptimizationTest {
    @Test fun countedRmsMatchesExactPrefixAndKeepsFilterHistory() {
        val counted = AWeightingFilter()
        val exact = AWeightingFilter()
        val buffer = shortArrayOf(100, -200, 300, -400, 32767, -32768)
        assertEquals(exact.processAndCalculateRMS(buffer.copyOf(4)), counted.processAndCalculateRMS(buffer, 4), 0.0)
        val next = shortArrayOf(-500, 600)
        assertEquals(exact.processAndCalculateRMS(next), counted.processAndCalculateRMS(next, 2), 0.0)
    }
    @Test fun emptyPrefixDoesNotConsumeStaleTailOrChangeState() {
        val filter = AWeightingFilter()
        assertEquals(0.0, filter.processAndCalculateRMS(shortArrayOf(32767), 0), 0.0)
        assertEquals(AWeightingFilter().process(10.0), filter.process(10.0), 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCountIsRejected() {
        AWeightingFilter().processAndCalculateRMS(shortArrayOf(1), 2)
    }
    @Test fun oneSortedWindowPreservesL10AndL90RankDefinitions() {
        val analyzer = NoiseAnalyzer()
        var result: NoiseAnalysisResult? = null
        for (i in 1..10) result = analyzer.analyze(NoiseFrame(i.toLong(), i.toFloat(), i.toFloat(), i.toFloat(), 1f))
        assertEquals(10f, result!!.l10, 0f)
        // Preserve floating-point rank/truncation behavior of original 1-0.90 expression.
        assertEquals(1f, result.l90, 0f)
        analyzer.reset()
        val single = analyzer.analyze(NoiseFrame(20, 30f, 30f, 30f, 1f))
        assertEquals(30f, single.l10, 0f)
        assertEquals(30f, single.l90, 0f)
    }
}
