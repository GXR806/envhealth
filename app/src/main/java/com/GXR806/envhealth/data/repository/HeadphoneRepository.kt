package com.GXR806.envhealth.data.repository

import com.GXR806.envhealth.data.local.room.dao.HeadphoneUsageDao
import com.GXR806.envhealth.data.local.room.entity.HeadphoneUsageEntity
import com.GXR806.envhealth.domain.model.DeviceType
import com.GXR806.envhealth.domain.model.HeadphoneUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 耳机使用数据仓库
 * 负责耳机使用记录的持久化与查询
 */
class HeadphoneRepository(
    private val headphoneUsageDao: HeadphoneUsageDao
) {
    
    suspend fun getCompletedIntervals(): List<HeadphoneUsage> =
        headphoneUsageDao.getCompletedIntervals().map { it.toDomain() }

    /**
     * 插入耳机使用记录
     */
    suspend fun insertUsage(usage: HeadphoneUsage): Long {
        val entity = usage.toEntity()
        return headphoneUsageDao.insert(entity)
    }
    
    /**
     * 更新耳机使用记录
     */
    suspend fun updateUsage(usage: HeadphoneUsage) {
        val entity = usage.toEntity()
        headphoneUsageDao.update(entity)
    }
    
    /**
     * 根据 ID 查询记录
     */
    suspend fun getUsageById(usageId: Long): HeadphoneUsage? {
        return headphoneUsageDao.getById(usageId)?.toDomain()
    }
    
    /**
     * 查询所有进行中的记录
     */
    fun getOngoingUsages(): Flow<List<HeadphoneUsage>> {
        return headphoneUsageDao.getOngoingUsages()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 按时间范围查询记录
     */
    fun getUsagesByTimeRange(startTime: Long, endTime: Long): Flow<List<HeadphoneUsage>> {
        return headphoneUsageDao.getUsagesByTimeRange(startTime, endTime)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 查询最近 N 条已完成的记录
     */
    fun getRecentCompletedUsages(limit: Int): Flow<List<HeadphoneUsage>> {
        return headphoneUsageDao.getRecentCompletedUsages(limit)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 查询时间范围内的平均音量
     */
    suspend fun getAverageVolume(startTime: Long, endTime: Long): Float? {
        return headphoneUsageDao.getAverageVolume(startTime, endTime)
    }
    
    /**
     * 查询时间范围内的最大音量
     */
    suspend fun getMaxVolume(startTime: Long, endTime: Long): Float? {
        return headphoneUsageDao.getMaxVolume(startTime, endTime)
    }
    
    /**
     * 统计时间范围内的使用时长总和（毫秒）
     */
    suspend fun getTotalDuration(startTime: Long, endTime: Long): Long? {
        return headphoneUsageDao.getTotalDuration(startTime, endTime)
    }
    
    /**
     * 删除指定时间之前的数据（数据保留策略：90 天）
     */
    suspend fun deleteBeforeTime(beforeTime: Long): Int {
        return headphoneUsageDao.deleteBeforeTime(beforeTime)
    }
    
    /**
     * 删除全部数据
     */
    suspend fun deleteAll() {
        headphoneUsageDao.deleteAll()
    }
    
    // Entity <-> Domain 转换
    
    private fun HeadphoneUsage.toEntity(): HeadphoneUsageEntity {
        return HeadphoneUsageEntity(
            id = id,
            startTime = startTime,
            endTime = endTime,
            avgVolumePercent = avgVolumePercent,
            maxVolumePercent = maxVolumePercent,
            deviceType = deviceType.name
        )
    }
    
    private fun HeadphoneUsageEntity.toDomain(): HeadphoneUsage {
        return HeadphoneUsage(
            id = id,
            startTime = startTime,
            endTime = endTime,
            avgVolumePercent = avgVolumePercent,
            maxVolumePercent = maxVolumePercent,
            deviceType = DeviceType.valueOf(deviceType)
        )
    }
}
