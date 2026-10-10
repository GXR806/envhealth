package com.GXR806.envhealth.data.repository

import com.GXR806.envhealth.domain.analysis.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/** 仅复用只读查询；BETWEEN 查询的上界减一毫秒以统一半开区间。 */
class HistoryRepository(
    private val noise: NoiseRepository, private val light: LightRepository,
    private val headphone: HeadphoneRepository, private val sleep: SleepRepository
) {
    fun observe(range: HistoryRange): Flow<HistoryData> = combine(
        noise.getSamplesByTimeRange(range.start, range.end - 1),
        light.getSamplesByTimeRange(range.start, range.end - 1),
        headphone.getOverlapping(range.start, range.end), sleep.getHistory(range.start, range.end)
    ) { n, l, h, s ->
        HistoryData(range, HistoryAggregator.aggregate(range, n, l, h, s), s)
    }.flowOn(Dispatchers.Default)
}
data class HistoryData(val range: HistoryRange, val charts: HistoryCharts, val reports: List<TimedReport>)
