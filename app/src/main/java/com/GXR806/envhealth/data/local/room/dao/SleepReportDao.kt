package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.GXR806.envhealth.data.local.room.entity.SleepReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * 睡眠报告数据访问对象
 */
@Dao
interface SleepReportDao {
    
    /**
     * 插入睡眠报告
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: SleepReportEntity): Long
    
    /**
     * 根据会话 ID 查询报告
     */
    @Query("SELECT * FROM sleep_reports WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: Long): SleepReportEntity?
    
    /**
     * 根据会话 ID 查询报告（Flow，响应式）
     */
    @Query("SELECT * FROM sleep_reports WHERE sessionId = :sessionId")
    fun getBySessionIdFlow(sessionId: Long): Flow<SleepReportEntity?>
    
    /**
     * 查询所有报告（按会话开始时间倒序）
     */
    @Query("""
        SELECT sr.* FROM sleep_reports sr
        INNER JOIN sleep_sessions ss ON sr.sessionId = ss.id
        ORDER BY ss.startTime DESC
    """)
    fun getAllReports(): Flow<List<SleepReportEntity>>
    
    /**
     * 查询最近 N 条报告
     */
    @Query("""
        SELECT sr.* FROM sleep_reports sr
        INNER JOIN sleep_sessions ss ON sr.sessionId = ss.id
        ORDER BY ss.startTime DESC
        LIMIT :limit
    """)
    fun getRecentReports(limit: Int): Flow<List<SleepReportEntity>>
    
    /** 只读联合查询，时间以会话开始毫秒为准，半开区间。 */
    @Query("""
        SELECT sr.*, ss.startTime AS sessionStartTime FROM sleep_reports sr
        INNER JOIN sleep_sessions ss ON sr.sessionId = ss.id
        WHERE ss.startTime >= :start AND ss.startTime < :end
        ORDER BY ss.startTime DESC, sr.id DESC
    """)
    fun getHistory(start: Long, end: Long): Flow<List<com.GXR806.envhealth.data.repository.HistoryReportRow>>

    /**
     * 查询平均总分
     */
    @Query("SELECT AVG(totalScore) FROM sleep_reports")
    suspend fun getAverageTotalScore(): Float?
    
    /**
     * 删除指定会话的报告
     */
    @Query("DELETE FROM sleep_reports WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long): Int
    
    /**
     * 删除全部数据
     */
    @Query("DELETE FROM sleep_reports")
    suspend fun deleteAll(): Int
}
