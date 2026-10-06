package com.GXR806.envhealth.data.repository

import com.GXR806.envhealth.data.local.room.dao.SleepReportDao
import com.GXR806.envhealth.data.local.room.dao.SleepSessionDao
import com.GXR806.envhealth.data.local.room.entity.SleepReportEntity
import com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity
import com.GXR806.envhealth.domain.model.SleepReport
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.domain.model.SleepSessionStatus
import com.GXR806.envhealth.domain.model.SleepSessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 睡眠数据仓库
 * 负责睡眠会话与报告的持久化与查询
 */
class SleepRepository(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepReportDao: SleepReportDao
) {
    
    // ========== 睡眠会话相关 ==========
    
    /**
     * 插入睡眠会话
     */
    suspend fun insertSession(session: SleepSession): Long {
        val entity = session.toEntity()
        return sleepSessionDao.insert(entity)
    }
    
    /**
     * 更新睡眠会话
     */
    suspend fun updateSession(session: SleepSession) {
        val entity = session.toEntity()
        sleepSessionDao.update(entity)
    }
    
    /**
     * 根据 ID 查询睡眠会话
     */
    suspend fun getSessionById(sessionId: Long): SleepSession? {
        return sleepSessionDao.getById(sessionId)?.toDomain()
    }
    
    /**
     * 根据 ID 查询睡眠会话（Flow，响应式）
     */
    fun getSessionByIdFlow(sessionId: Long): Flow<SleepSession?> {
        return sleepSessionDao.getByIdFlow(sessionId)
            .map { it?.toDomain() }
    }
    
    /**
     * 查询所有进行中的会话
     */
    fun getOngoingSessions(): Flow<List<SleepSession>> {
        return sleepSessionDao.getOngoingSessions()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 按时间范围查询睡眠会话
     */
    fun getSessionsByTimeRange(startTime: Long, endTime: Long): Flow<List<SleepSession>> {
        return sleepSessionDao.getSessionsByTimeRange(startTime, endTime)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 查询最近 N 条已完成的会话
     */
    fun getRecentCompletedSessions(limit: Int): Flow<List<SleepSession>> {
        return sleepSessionDao.getRecentCompletedSessions(limit)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 统计时间范围内的会话数量
     */
    suspend fun getSessionCountByTimeRange(startTime: Long, endTime: Long): Int {
        return sleepSessionDao.getCountByTimeRange(startTime, endTime)
    }
    
    /**
     * 删除指定时间之前的会话（数据保留策略：90 天）
     */
    suspend fun deleteSessionsBeforeTime(beforeTime: Long): Int {
        return sleepSessionDao.deleteBeforeTime(beforeTime)
    }
    
    /**
     * 删除全部会话
     */
    suspend fun deleteAllSessions() {
        sleepSessionDao.deleteAll()
    }
    
    // ========== 睡眠报告相关 ==========
    
    /**
     * 插入睡眠报告
     */
    suspend fun insertReport(report: SleepReport): Long {
        val entity = report.toEntity()
        return sleepReportDao.insert(entity)
    }
    
    /**
     * 根据会话 ID 查询报告
     */
    suspend fun getReportBySessionId(sessionId: Long): SleepReport? {
        return sleepReportDao.getBySessionId(sessionId)?.toDomain()
    }
    
    /**
     * 根据会话 ID 查询报告（Flow，响应式）
     */
    fun getReportBySessionIdFlow(sessionId: Long): Flow<SleepReport?> {
        return sleepReportDao.getBySessionIdFlow(sessionId)
            .map { it?.toDomain() }
    }
    
    /**
     * 查询所有报告（按会话开始时间倒序）
     */
    fun getAllReports(): Flow<List<SleepReport>> {
        return sleepReportDao.getAllReports()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 查询最近 N 条报告
     */
    fun getRecentReports(limit: Int): Flow<List<SleepReport>> {
        return sleepReportDao.getRecentReports(limit)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 查询平均总分
     */
    suspend fun getAverageTotalScore(): Float? {
        return sleepReportDao.getAverageTotalScore()
    }
    
    /**
     * 删除指定会话的报告
     */
    suspend fun deleteReportBySessionId(sessionId: Long): Int {
        return sleepReportDao.deleteBySessionId(sessionId)
    }
    
    /**
     * 删除全部报告
     */
    suspend fun deleteAllReports() {
        sleepReportDao.deleteAll()
    }
    
    // ========== Entity <-> Domain 转换 ==========
    
    private fun SleepSession.toEntity(): SleepSessionEntity {
        return SleepSessionEntity(
            id = id,
            startTime = startTime,
            endTime = endTime,
            type = type.name,
            status = status.name
        )
    }
    
    private fun SleepSessionEntity.toDomain(): SleepSession {
        return SleepSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            type = SleepSessionType.valueOf(type),
            status = SleepSessionStatus.valueOf(status)
        )
    }
    
    private fun SleepReport.toEntity(): SleepReportEntity {
        return SleepReportEntity(
            sessionId = sessionId,
            noiseScore = noiseScore,
            lightScore = lightScore,
            regularityScore = regularityScore,
            totalScore = totalScore,
            summary = summary
        )
    }
    
    private fun SleepReportEntity.toDomain(): SleepReport {
        return SleepReport(
            id = id,
            sessionId = sessionId,
            noiseScore = noiseScore,
            lightScore = lightScore,
            regularityScore = regularityScore,
            totalScore = totalScore,
            summary = summary
        )
    }
}
