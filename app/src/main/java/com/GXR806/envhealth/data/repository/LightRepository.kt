package com.GXR806.envhealth.data.repository

import com.GXR806.envhealth.data.local.room.dao.LightSampleDao
import com.GXR806.envhealth.data.local.room.entity.LightSampleEntity
import com.GXR806.envhealth.domain.model.LightLevel
import com.GXR806.envhealth.domain.model.LightSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 光照数据仓库
 * 负责光照采样数据的持久化与查询
 */
class LightRepository(
    private val lightSampleDao: LightSampleDao
) {
    
    /**
     * 插入单条光照采样
     */
    suspend fun insertSample(sample: LightSample): Long {
        val entity = sample.toEntity()
        return lightSampleDao.insert(entity)
    }
    
    /**
     * 批量插入光照采样
     */
    suspend fun insertSamples(samples: List<LightSample>) {
        val entities = samples.map { it.toEntity() }
        lightSampleDao.insertAll(entities)
    }
    
    /**
     * 按时间范围查询光照采样（Flow，响应式）
     */
    fun getSamplesByTimeRange(startTime: Long, endTime: Long): Flow<List<LightSample>> {
        return lightSampleDao.getSamplesByTimeRange(startTime, endTime)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 按时间范围查询光照采样（挂起函数，一次性）
     */
    suspend fun getSamplesByTimeRangeOnce(startTime: Long, endTime: Long): List<LightSample> {
        return lightSampleDao.getSamplesByTimeRangeOnce(startTime, endTime)
            .map { it.toDomain() }
    }
    
    /**
     * 查询时间范围内的平均亮度
     */
    suspend fun getAverageLuma(startTime: Long, endTime: Long): Float? {
        return lightSampleDao.getAverageLuma(startTime, endTime)
    }
    
    /**
     * 查询时间范围内的平均 lux
     */
    suspend fun getAverageLux(startTime: Long, endTime: Long): Float? {
        return lightSampleDao.getAverageLux(startTime, endTime)
    }
    
    /**
     * 删除指定时间之前的数据（数据保留策略：7 天）
     */
    suspend fun deleteBeforeTime(beforeTime: Long): Int {
        return lightSampleDao.deleteBeforeTime(beforeTime)
    }
    
    /**
     * 查询总记录数
     */
    suspend fun getCount(): Int {
        return lightSampleDao.getCount()
    }
    
    /**
     * 删除全部数据
     */
    suspend fun deleteAll() {
        lightSampleDao.deleteAll()
    }
    
    // Entity <-> Domain 转换
    
    private fun LightSample.toEntity(): LightSampleEntity {
        return LightSampleEntity(
            timestamp = timestamp,
            avgLuma = avgLuma,
            estimatedLux = estimatedLux,
            level = level.name
        )
    }
    
    private fun LightSampleEntity.toDomain(): LightSample {
        return LightSample(
            timestamp = timestamp,
            avgLuma = avgLuma,
            estimatedLux = estimatedLux,
            level = LightLevel.valueOf(level)
        )
    }
}
