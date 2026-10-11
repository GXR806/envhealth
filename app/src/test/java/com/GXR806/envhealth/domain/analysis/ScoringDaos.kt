package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.local.room.dao.*
import com.GXR806.envhealth.data.local.room.entity.*
import com.GXR806.envhealth.data.repository.HistoryReportRow
import kotlinx.coroutines.flow.flowOf

/** In-memory DAO fakes model query boundaries, ordering and status, not scoring. */
internal class ScoringNoiseDao : NoiseSampleDao {
    private val rows = mutableListOf<NoiseSampleEntity>()
    var lastRange: Pair<Long, Long>? = null
    var failure: Exception? = null
    private fun range(start: Long, end: Long) = rows.filter { it.timestamp in start..end }.sortedBy { it.timestamp }
    override suspend fun insert(sample: NoiseSampleEntity): Long { rows.add(sample); return rows.size.toLong() }
    override suspend fun insertAll(samples: List<NoiseSampleEntity>) = samples.map { insert(it) }
    override fun getSamplesByTimeRange(startTime: Long, endTime: Long) = flowOf(range(startTime, endTime))
    override suspend fun getSamplesByTimeRangeOnce(startTime: Long, endTime: Long): List<NoiseSampleEntity> {
        failure?.let { throw it }
        lastRange = startTime to endTime
        return range(startTime, endTime)
    }
    override suspend fun getAverageDb(startTime: Long, endTime: Long) = range(startTime, endTime).takeIf { it.isNotEmpty() }?.map { it.leqDb }?.average()?.toFloat()
    override suspend fun getMaxDb(startTime: Long, endTime: Long) = range(startTime, endTime).maxOfOrNull { it.maxDb }
    override suspend fun getEventCount(startTime: Long, endTime: Long) = range(startTime, endTime).count { it.isEvent }
    override suspend fun deleteBeforeTime(beforeTime: Long): Int { val count = rows.count { it.timestamp < beforeTime }; rows.removeAll { it.timestamp < beforeTime }; return count }
    override suspend fun deleteAll(): Int { val count = rows.size; rows.clear(); return count }
    override suspend fun getCount() = rows.size
}

internal class ScoringLightDao : LightSampleDao {
    private val rows = mutableListOf<LightSampleEntity>()
    var lastRange: Pair<Long, Long>? = null
    private fun range(start: Long, end: Long) = rows.filter { it.timestamp in start..end }.sortedBy { it.timestamp }
    override suspend fun insert(sample: LightSampleEntity): Long { rows.add(sample); return rows.size.toLong() }
    override suspend fun insertAll(samples: List<LightSampleEntity>) = samples.map { insert(it) }
    override fun getSamplesByTimeRange(startTime: Long, endTime: Long) = flowOf(range(startTime, endTime))
    override suspend fun getSamplesByTimeRangeOnce(startTime: Long, endTime: Long): List<LightSampleEntity> { lastRange = startTime to endTime; return range(startTime, endTime) }
    override suspend fun getAverageLuma(startTime: Long, endTime: Long) = range(startTime, endTime).takeIf { it.isNotEmpty() }?.map { it.avgLuma }?.average()?.toFloat()
    override suspend fun getAverageLux(startTime: Long, endTime: Long) = range(startTime, endTime).mapNotNull { it.estimatedLux }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    override suspend fun deleteBeforeTime(beforeTime: Long): Int { val count = rows.count { it.timestamp < beforeTime }; rows.removeAll { it.timestamp < beforeTime }; return count }
    override suspend fun deleteAll(): Int { val count = rows.size; rows.clear(); return count }
    override suspend fun getCount() = rows.size
}

internal class ScoringSessionDao : SleepSessionDao {
    private val rows = mutableListOf<SleepSessionEntity>()
    var lastLimit: Int? = null
    override suspend fun insert(session: SleepSessionEntity): Long { rows.removeAll { it.id == session.id }; rows.add(session); return session.id }
    override suspend fun update(session: SleepSessionEntity): Int { val index = rows.indexOfFirst { it.id == session.id }; if (index < 0) return 0; rows[index] = session; return 1 }
    override suspend fun getById(sessionId: Long) = rows.firstOrNull { it.id == sessionId }
    override fun getByIdFlow(sessionId: Long) = flowOf(rows.firstOrNull { it.id == sessionId })
    override fun getOngoingSessions() = flowOf(rows.filter { it.status == "ONGOING" }.sortedByDescending { it.startTime })
    override fun getSessionsByTimeRange(startTime: Long, endTime: Long) = flowOf(rows.filter { it.startTime in startTime..endTime }.sortedByDescending { it.startTime })
    override fun getRecentCompletedSessions(limit: Int): kotlinx.coroutines.flow.Flow<List<SleepSessionEntity>> { lastLimit = limit; return flowOf(rows.filter { it.status == "COMPLETED" }.sortedByDescending { it.startTime }.take(limit)) }
    override suspend fun getCountByTimeRange(startTime: Long, endTime: Long) = rows.count { it.startTime in startTime..endTime }
    override suspend fun deleteBeforeTime(beforeTime: Long): Int { val count = rows.count { it.startTime < beforeTime }; rows.removeAll { it.startTime < beforeTime }; return count }
    override suspend fun deleteCompletedBeforeEnd(beforeTime: Long): Int { val old = rows.filter { it.status != "ONGOING" && (it.endTime ?: Long.MAX_VALUE) < beforeTime }; rows.removeAll(old.toSet()); return old.size }
    override suspend fun deleteAll(): Int { val count = rows.size; rows.clear(); return count }
}

/** Fail fast if the scoring analyzer starts reading/writing reports unexpectedly. */
internal class ScoringReportDao : SleepReportDao {
    override suspend fun insert(report: SleepReportEntity): Long = error("Scoring must not write reports")
    override suspend fun getBySessionId(sessionId: Long): SleepReportEntity? = error("Unexpected report query")
    override fun getBySessionIdFlow(sessionId: Long) = flowOf<SleepReportEntity?>(null)
    override fun getAllReports() = flowOf(emptyList<SleepReportEntity>())
    override fun getRecentReports(limit: Int) = flowOf(emptyList<SleepReportEntity>())
    override fun getHistory(start: Long, end: Long) = flowOf(emptyList<HistoryReportRow>())
    override suspend fun getAverageTotalScore(): Float? = null
    override suspend fun deleteBySessionId(sessionId: Long) = 0
    override suspend fun deleteAll() = 0
}
