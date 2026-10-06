package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.GXR806.envhealth.data.local.room.entity.NoiseSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 噪音采样数据访问对象
 */
@Dao
interface NoiseSampleDao {
    
    /**
     * 插入单条噪音采样
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: NoiseSampleEntity): Long
    
    /**
     * 批量插入噪音采样
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<NoiseSampleEntity>): List<Long>
    
    /**
     * 按时间范围查询噪音采样（Flow，响应式）
     */
    @Query("SELECT * FROM noise_samples WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getSamplesByTimeRange(startTime: Long, endTime: Long): Flow<List<NoiseSampleEntity>>
    
    /**
     * 按时间范围查询噪音采样（挂起函数，一次性）
     */
    @Query("SELECT * FROM noise_samples WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getSamplesByTimeRangeOnce(startTime: Long, endTime: Long): List<NoiseSampleEntity>
    
    /**
     * 查询时间范围内的平均分贝
     */
    @Query("SELECT AVG(leqDb) FROM noise_samples WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getAverageDb(startTime: Long, endTime: Long): Float?
    
    /**
     * 查询时间范围内的最大分贝
     */
    @Query("SELECT MAX(maxDb) FROM noise_samples WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getMaxDb(startTime: Long, endTime: Long): Float?
    
    /**
     * 查询时间范围内的噪音事件数量
     */
    @Query("SELECT COUNT(*) FROM noise_samples WHERE timestamp BETWEEN :startTime AND :endTime AND isEvent = 1")
    suspend fun getEventCount(startTime: Long, endTime: Long): Int
    
    /**
     * 删除指定时间之前的数据（数据保留策略：7 天）
     */
    @Query("DELETE FROM noise_samples WHERE timestamp < :beforeTime")
    suspend fun deleteBeforeTime(beforeTime: Long): Int
    
    /**
     * 删除全部数据
     */
    @Query("DELETE FROM noise_samples")
    suspend fun deleteAll(): Int
    
    /**
     * 查询总记录数
     */
    @Query("SELECT COUNT(*) FROM noise_samples")
    suspend fun getCount(): Int
}
