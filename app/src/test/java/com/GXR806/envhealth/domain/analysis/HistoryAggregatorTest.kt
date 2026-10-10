package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HistoryAggregatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ms(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun charts(range: HistoryRange, noise: List<NoiseSample> = emptyList(),
                       light: List<LightSample> = emptyList(), h: List<HeadphoneUsage> = emptyList(),
                       reports: List<TimedReport> = emptyList()) = HistoryAggregator.aggregate(range, noise, light, h, reports)
    @Test fun `自然日边界为本地午夜且二十四个小时桶`() {
        val range = HistoryAggregator.range(ms("2026-02-18T23:59:59"), HistoryPeriod.DAY, zone)
        assertEquals(ms("2026-02-18T00:00:00"), range.start)
        assertEquals(ms("2026-02-19T00:00:00"), range.end)
        assertEquals(24, range.buckets.size)
    }
    @Test fun `自然周从周一开始且跨月`() {
        val range = HistoryAggregator.range(ms("2026-03-01T12:00:00"), HistoryPeriod.WEEK, zone)
        assertEquals(ms("2026-02-23T00:00:00"), range.start)
        assertEquals(ms("2026-03-02T00:00:00"), range.end)
        assertEquals(7, range.buckets.size)
    }
    @Test fun `闰年自然月有二十九天`() {
        val range = HistoryAggregator.range(ms("2024-02-18T12:00:00"), HistoryPeriod.MONTH, zone)
        assertEquals(ms("2024-02-01T00:00:00"), range.start)
        assertEquals(ms("2024-03-01T00:00:00"), range.end)
        assertEquals(29, range.buckets.size)
    }
    @Test fun `夏令时日不硬编码二十四小时`() {
        val z = ZoneId.of("America/New_York")
        val now = LocalDate.of(2026, 3, 8).atStartOfDay(z).toInstant().toEpochMilli()
        val range = HistoryAggregator.range(now, HistoryPeriod.DAY, z)
        assertEquals(23, range.buckets.size)
        assertEquals(23 * 3_600_000L, range.end - range.start)
    }
    @Test fun `空数据所有序列保留缺口而非假零`() {
        val range = HistoryAggregator.range(ms("2026-02-18T12:00:00"), HistoryPeriod.DAY, zone)
        val c = charts(range)
        assertTrue((c.noise + c.light + c.headphone + c.sleep).all { it.value == null })
    }
    @Test fun `毫秒半开边界噪音均值和评分Canvas输入`() {
        val range = HistoryAggregator.range(ms("2026-02-18T12:00:00"), HistoryPeriod.DAY, zone)
        fun sample(t: Long, db: Float) = NoiseSample(timestamp = t, leqDb = db, maxDb = db, peakDb = db, isEvent = false, burstCount = 0)
        val report = SleepReport(sessionId = 1, noiseScore = 80f, lightScore = 80f, regularityScore = null, totalScore = 80f, summary = "测试")
        val c = charts(range, noise = listOf(sample(range.start - 1, 100f), sample(range.start, 20f),
            sample(range.start + 3_599_999L, 40f), sample(range.start + 3_600_000L, 50f), sample(range.end, 100f)),
            reports = listOf(TimedReport(range.start, report), TimedReport(range.end, report)))
        assertEquals(30f, c.noise[0].value!!, 0f)
        assertEquals(50f, c.noise[1].value!!, 0f)
        assertEquals(80f, c.sleep[0].value!!, 0f)
        assertEquals(1, c.sleep.count { it.value != null })
    }
    @Test fun `跨午夜区间裁剪和毫秒转分钟堆叠不丢精度`() {
        val range = HistoryAggregator.range(ms("2026-02-18T12:00:00"), HistoryPeriod.DAY, zone)
        fun usage(start: Long, end: Long?, type: DeviceType) = HeadphoneUsage(startTime = start, endTime = end,
            avgVolumePercent = 30f, maxVolumePercent = 40f, deviceType = type)
        val c = charts(range, h = listOf(usage(range.start - 1000, range.start + 1500, DeviceType.WIRED),
            usage(range.start, range.start + 500, DeviceType.BLUETOOTH),
            usage(range.start, null, DeviceType.WIRED), usage(range.end, range.end + 1000, DeviceType.WIRED)))
        assertEquals(0.025f, c.headphone[0].value!!, 0.00001f)
        assertEquals(500 / 60_000f, c.headphone[0].secondary, 0.00001f)
        assertTrue(c.headphone.drop(1).all { it.value == null })
    }
    @Test fun `光照分布按采样计数忽略降级空值`() {
        val range = HistoryAggregator.range(ms("2026-02-18T12:00:00"), HistoryPeriod.DAY, zone)
        val values = listOf<Float?>(null, 4f, 5f, 50f, 300f, Float.NaN)
        val samples = values.map { LightSample(timestamp = range.start, avgLuma = 20f, estimatedLux = it, level = LightLevel.DARK) }
        val c = charts(range, light = samples)
        assertEquals(listOf(1f, 1f, 1f, 1f), c.light.map { it.value })
    }
}
