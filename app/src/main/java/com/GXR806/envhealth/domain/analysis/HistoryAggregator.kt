package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.domain.model.*
import java.time.*

/** 本地自然日、周一开始的自然周、自然月；所有边界都是毫秒半开区间。 */
enum class HistoryPeriod(val title: String) { DAY("日"), WEEK("周"), MONTH("月") }
data class HistoryBucket(val start: Long, val end: Long, val label: String)
data class HistoryRange(val start: Long, val end: Long, val buckets: List<HistoryBucket>)
data class TimedReport(val timestamp: Long, val report: SleepReport)
data class ChartPoint(val label: String, val value: Float?, val secondary: Float = 0f)
data class HistoryCharts(
    val noise: List<ChartPoint>, val light: List<ChartPoint>,
    val headphone: List<ChartPoint>, val sleep: List<ChartPoint>
)

object HistoryAggregator {
    fun range(nowMs: Long, period: HistoryPeriod, zone: ZoneId): HistoryRange {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val first = when (period) {
            HistoryPeriod.DAY -> date
            HistoryPeriod.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong())
            HistoryPeriod.MONTH -> date.withDayOfMonth(1)
        }
        val last = when (period) {
            HistoryPeriod.DAY -> first.plusDays(1)
            HistoryPeriod.WEEK -> first.plusDays(7)
            HistoryPeriod.MONTH -> first.plusMonths(1)
        }
        val start = first.atStartOfDay(zone)
        val end = last.atStartOfDay(zone)
        val buckets = mutableListOf<HistoryBucket>()
        var cursor = start
        while (cursor < end) {
            val next = if (period == HistoryPeriod.DAY) cursor.plusHours(1) else cursor.plusDays(1)
            val label = if (period == HistoryPeriod.DAY) "${cursor.hour}时" else "${cursor.monthValue}/${cursor.dayOfMonth}"
            buckets += HistoryBucket(cursor.toInstant().toEpochMilli(), next.toInstant().toEpochMilli(), label)
            cursor = next
        }
        return HistoryRange(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), buckets)
    }

    /** 噪音为采样 Leq 算术均值（非重新计算声学 Leq）；无采样不填零、不连缺口。 */
    fun aggregate(range: HistoryRange, noise: List<NoiseSample>, light: List<LightSample>,
                  headphones: List<HeadphoneUsage>, reports: List<TimedReport>): HistoryCharts {
        fun mean(values: List<Float>): Float? = values.filter { it.isFinite() }.takeIf { it.isNotEmpty() }
            ?.map { it.toDouble() }?.average()?.toFloat()
        val n = range.buckets.map { b -> ChartPoint(b.label,
            mean(noise.filter { it.timestamp >= b.start && it.timestamp < b.end }.map { it.leqDb })) }
        // 光照分布按实际采样数量，而非推测照射时长。
        val lux = light.filter { it.timestamp >= range.start && it.timestamp < range.end }
            .mapNotNull { it.estimatedLux?.takeIf { value -> value.isFinite() } }
        val l = listOf("<5", "5–50", "50–300", "≥300").mapIndexed { index, label ->
            val count = lux.count { value -> when {
                value < 5 -> index == 0
                value < 50 -> index == 1
                value < 300 -> index == 2
                else -> index == 3
            } }
            ChartPoint(label, if (lux.isEmpty()) null else count.toFloat())
        }
        val h = range.buckets.map { b ->
            var wired = 0L; var bluetooth = 0L
            headphones.forEach { record ->
                record.endTime?.let { end ->
                    val duration = (minOf(end, b.end) - maxOf(record.startTime, b.start)).coerceAtLeast(0)
                    if (record.deviceType == DeviceType.BLUETOOTH) bluetooth += duration else wired += duration
                }
            }
            ChartPoint(b.label, if (wired + bluetooth == 0L) null else wired / 60_000f, bluetooth / 60_000f)
        }
        val s = range.buckets.map { b -> ChartPoint(b.label,
            mean(reports.filter { it.timestamp >= b.start && it.timestamp < b.end }.map { it.report.totalScore })) }
        return HistoryCharts(n, l, h, s)
    }
}
