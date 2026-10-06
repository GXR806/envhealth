package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 睡眠会话数据访问对象
 */
@Dao
interface SleepSessionDao {
    
    /**
     * 插入睡眠会话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSessionEntity): Long
    
    /**
     * 更新睡眠会话
     */
    @Update
    suspend fun update(session: SleepSessionEntity): Int
    
    /**
     * 根据 ID 查询睡眠会话
     */
    @Query("SELECT * FROM sleep_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): SleepSessionEntity?
    
    /**
     * 根据 ID 查询睡眠会话（Flow，响应式）
     */
    @Query("SELECT * FROM sleep_sessions WHERE id = :sessionId")
    fun getByIdFlow(sessionId: Long): Flow<SleepSessionEntity?>
    
    /**
     * 查询所有进行中的会话
     */
    @Query("SELECT * FROM sleep_sessions WHERE status = 'ONGOING' ORDER BY startTime DESC")
    fun getOngoingSessions(): Flow<List<SleepSessionEntity>>
    
    /**
     * 按时间范围查询睡眠会话
     */
    @Query("SELECT * FROM sleep_sessions WHERE startTime BETWEEN :startTime AND :endTime ORDER BY startTime DESC")
    fun getSessionsByTimeRange(startTime: Long, endTime: Long): Flow<List<SleepSessionEntity>>
    
    /**
     * 查询最近 N 条已完成的会话
     */
    @Query("SELECT * FROM sleep_sessions WHERE status = 'COMPLETED' ORDER BY startTime DESC LIMIT :limit")
    fun getRecentCompletedSessions(limit: Int): Flow<List<SleepSessionEntity>>
    
    /**
     * 统计时间范围内的会话数量
     */
    @Query("SELECT COUNT(*) FROM sleep_sessions WHERE startTime BETWEEN :startTime AND :endTime")
    suspend fun getCountByTimeRange(startTime: Long, endTime: Long): Int
    
    /**
     * 删除指定时间之前的数据（数据保留策略：90 天）
     */
    @Query("DELETE FROM sleep_sessions WHERE startTime < :beforeTime")
    suspend fun deleteBeforeTime(beforeTime: Long): Int
    
    /**
     * 删除全部数据
     */
    /** 90 天聚合清理：保护进行中会话，报告由外键 CASCADE 同事务删除。 */
    @Query("DELETE FROM sleep_sessions WHERE status != 'ONGOING' AND endTime IS NOT NULL AND endTime < :beforeTime")
    suspend fun deleteCompletedBeforeEnd(beforeTime: Long): Int

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll(): Int
}
