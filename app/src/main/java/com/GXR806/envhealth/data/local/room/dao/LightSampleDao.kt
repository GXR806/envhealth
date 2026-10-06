package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.GXR806.envhealth.data.local.room.entity.LightSampleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 光照采样数据访问对象
 */
@Dao
interface LightSampleDao {
    
    /**
     * 插入单条光照采样
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: LightSampleEntity): Long
    
    /**
     * 批量插入光照采样
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<LightSampleEntity>): List<Long>
    
    /**
     * 按时间范围查询光照采样（Flow，响应式）
     */
    @Query("SELECT * FROM light_samples WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getSamplesByTimeRange(startTime: Long, endTime: Long): Flow<List<LightSampleEntity>>
    
    /**
     * 按时间范围查询光照采样（挂起函数，一次性）
     */
    @Query("SELECT * FROM light_samples WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getSamplesByTimeRangeOnce(startTime: Long, endTime: Long): List<LightSampleEntity>
    
    /**
     * 查询时间范围内的平均亮度
     */
    @Query("SELECT AVG(avgLuma) FROM light_samples WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getAverageLuma(startTime: Long, endTime: Long): Float?
    
    /**
     * 查询时间范围内的平均 lux
     */
    @Query("SELECT AVG(estimatedLux) FROM light_samples WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getAverageLux(startTime: Long, endTime: Long): Float?
    
    /**
     * 删除指定时间之前的数据（数据保留策略：7 天）
     */
    @Query("DELETE FROM light_samples WHERE timestamp < :beforeTime")
    suspend fun deleteBeforeTime(beforeTime: Long): Int
    
    /**
     * 删除全部数据
     */
    @Query("DELETE FROM light_samples")
    suspend fun deleteAll(): Int
    
    /**
     * 查询总记录数
     */
    @Query("SELECT COUNT(*) FROM light_samples")
    suspend fun getCount(): Int
}
