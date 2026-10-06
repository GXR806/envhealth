package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.GXR806.envhealth.data.local.room.entity.HeadphoneUsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 耳机使用记录数据访问对象
 */
@Dao
interface HeadphoneUsageDao {
    
    /**
     * 插入耳机使用记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: HeadphoneUsageEntity): Long
    
    /**
     * 更新耳机使用记录
     */
    @Update
    suspend fun update(usage: HeadphoneUsageEntity): Int
    
    /**
     * 根据 ID 查询记录
     */
    @Query("SELECT * FROM headphone_usage WHERE id = :usageId")
    suspend fun getById(usageId: Long): HeadphoneUsageEntity?
    
    /**
     * 查询所有进行中的记录
     */
    @Query("SELECT * FROM headphone_usage WHERE endTime IS NULL ORDER BY startTime DESC")
    fun getOngoingUsages(): Flow<List<HeadphoneUsageEntity>>
    
    /** 查询全部已完成的播放区间，交由领域层按时间边界裁剪。 */
    @Query("SELECT * FROM headphone_usage WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun getCompletedIntervals(): List<HeadphoneUsageEntity>

    /**
     * 按时间范围查询记录
     */
    @Query("SELECT * FROM headphone_usage WHERE startTime BETWEEN :startTime AND :endTime ORDER BY startTime DESC")
    fun getUsagesByTimeRange(startTime: Long, endTime: Long): Flow<List<HeadphoneUsageEntity>>
    
    /**
     * 查询最近 N 条已完成的记录
     */
    @Query("SELECT * FROM headphone_usage WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT :limit")
    fun getRecentCompletedUsages(limit: Int): Flow<List<HeadphoneUsageEntity>>
    
    /**
     * 查询时间范围内的平均音量
     */
    @Query("SELECT AVG(avgVolumePercent) FROM headphone_usage WHERE startTime BETWEEN :startTime AND :endTime")
    suspend fun getAverageVolume(startTime: Long, endTime: Long): Float?
    
    /**
     * 查询时间范围内的最大音量
     */
    @Query("SELECT MAX(maxVolumePercent) FROM headphone_usage WHERE startTime BETWEEN :startTime AND :endTime")
    suspend fun getMaxVolume(startTime: Long, endTime: Long): Float?
    
    /**
     * 统计时间范围内的使用时长总和（毫秒）
     */
    @Query("""
        SELECT SUM(endTime - startTime) 
        FROM headphone_usage 
        WHERE startTime BETWEEN :startTime AND :endTime 
        AND endTime IS NOT NULL
    """)
    suspend fun getTotalDuration(startTime: Long, endTime: Long): Long?
    
    /**
     * 删除指定时间之前的数据（数据保留策略：90 天）
     */
    @Query("DELETE FROM headphone_usage WHERE startTime < :beforeTime")
    suspend fun deleteBeforeTime(beforeTime: Long): Int
    
    /**
     * 删除全部数据
     */
    @Query("DELETE FROM headphone_usage")
    suspend fun deleteAll(): Int
}
