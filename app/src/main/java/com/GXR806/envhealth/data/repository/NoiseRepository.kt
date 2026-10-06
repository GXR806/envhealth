package com.GXR806.envhealth.data.repository

import com.GXR806.envhealth.data.local.room.dao.NoiseSampleDao
import com.GXR806.envhealth.data.local.room.entity.NoiseSampleEntity
import com.GXR806.envhealth.domain.model.NoiseLevel
import com.GXR806.envhealth.domain.model.NoiseSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 噪音数据仓库
 * 负责噪音采样数据的持久化与查询
 */
class NoiseRepository(
    private val noiseSampleDao: NoiseSampleDao
) {
    
    /**
     * 插入单条噪音采样
     */
    suspend fun insertSample(sample: NoiseSample): Long {
        val entity = sample.toEntity()
        return noiseSampleDao.insert(entity)
    }
    
    /**
     * 批量插入噪音采样
     */
    suspend fun insertSamples(samples: List<NoiseSample>) {
        val entities = samples.map { it.toEntity() }
        noiseSampleDao.insertAll(entities)
    }
    
    /**
     * 按时间范围查询噪音采样（Flow，响应式）
     */
    fun getSamplesByTimeRange(startTime: Long, endTime: Long): Flow<List<NoiseSample>> {
        return noiseSampleDao.getSamplesByTimeRange(startTime, endTime)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 按时间范围查询噪音采样（挂起函数，一次性）
     */
    suspend fun getSamplesByTimeRangeOnce(startTime: Long, endTime: Long): List<NoiseSample> {
        return noiseSampleDao.getSamplesByTimeRangeOnce(startTime, endTime)
            .map { it.toDomain() }
    }
    
    /**
     * 查询时间范围内的平均分贝
     */
    suspend fun getAverageDb(startTime: Long, endTime: Long): Float? {
        return noiseSampleDao.getAverageDb(startTime, endTime)
    }
    
    /**
     * 查询时间范围内的最大分贝
     */
    suspend fun getMaxDb(startTime: Long, endTime: Long): Float? {
        return noiseSampleDao.getMaxDb(startTime, endTime)
    }
    
    /**
     * 查询时间范围内的噪音事件数量
     */
    suspend fun getEventCount(startTime: Long, endTime: Long): Int {
        return noiseSampleDao.getEventCount(startTime, endTime)
    }
    
    /**
     * 删除指定时间之前的数据（数据保留策略：7 天）
     */
    suspend fun deleteBeforeTime(beforeTime: Long): Int {
        return noiseSampleDao.deleteBeforeTime(beforeTime)
    }
    
    /**
     * 查询总记录数
     */
    suspend fun getCount(): Int {
        return noiseSampleDao.getCount()
    }
    
    /**
     * 删除全部数据
     */
    suspend fun deleteAll() {
        noiseSampleDao.deleteAll()
    }
    
    // Entity <-> Domain 转换
    
    private fun NoiseSample.toEntity(): NoiseSampleEntity {
        return NoiseSampleEntity(
            timestamp = timestamp,
            leqDb = leqDb,
            maxDb = maxDb,
            peakDb = peakDb,
            isEvent = isEvent
        )
    }
    
    private fun NoiseSampleEntity.toDomain(): NoiseSample {
        return NoiseSample(
            timestamp = timestamp,
            leqDb = leqDb,
            maxDb = maxDb,
            peakDb = peakDb,
            isEvent = isEvent
        )
    }
}
