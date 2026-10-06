package com.GXR806.envhealth.data.repository

import com.GXR806.envhealth.data.local.room.dao.AdviceDao
import com.GXR806.envhealth.data.local.room.entity.AdviceEntity
import com.GXR806.envhealth.domain.model.AdviceCategory
import com.GXR806.envhealth.domain.model.AdviceItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 健康建议数据仓库
 * 负责建议的持久化与查询
 */
class AdviceRepository(
    private val adviceDao: AdviceDao
) {
    
    /**
     * 插入建议
     */
    suspend fun insertAdvice(advice: AdviceItem): Long {
        val entity = advice.toEntity()
        return adviceDao.insert(entity)
    }
    
    /**
     * 批量插入建议
     */
    suspend fun insertAllAdvice(adviceList: List<AdviceItem>): List<Long> {
        val entities = adviceList.map { it.toEntity() }
        return adviceDao.insertAll(entities)
    }
    
    /**
     * 查询所有建议（按优先级倒序，创建时间倒序）
     */
    fun getAllAdvice(): Flow<List<AdviceItem>> {
        return adviceDao.getAllAdvice()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 按类别查询建议
     */
    fun getAdviceByCategory(category: AdviceCategory): Flow<List<AdviceItem>> {
        return adviceDao.getAdviceByCategory(category.name)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 查询最近 N 条建议
     */
    fun getRecentAdvice(limit: Int): Flow<List<AdviceItem>> {
        return adviceDao.getRecentAdvice(limit)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    /**
     * 根据 ID 查询建议
     */
    suspend fun getById(adviceId: Long): AdviceItem? {
        return adviceDao.getById(adviceId)?.toDomain()
    }
    
    /**
     * 删除指定 ID 的建议
     */
    suspend fun deleteById(adviceId: Long): Int {
        return adviceDao.deleteById(adviceId)
    }
    
    /**
     * 删除指定时间之前的数据（数据保留策略：90 天）
     */
    suspend fun deleteBeforeTime(beforeTime: Long): Int {
        return adviceDao.deleteBeforeTime(beforeTime)
    }
    
    /**
     * 删除全部数据
     */
    suspend fun deleteAll(): Int {
        return adviceDao.deleteAll()
    }
    
    /**
     * 查询指定时间范围内已生成的建议（用于去重）
     * 
     * @param sinceTime 起始时间（毫秒）
     * @return 建议列表（用于规则去重）
     */
    suspend fun getAdviceSince(sinceTime: Long): List<AdviceItem> {
        // 使用 Flow.first() 获取第一个值
        val allEntities = adviceDao.getAllAdvice().first()
        return allEntities
            .filter { it.createdAt >= sinceTime }
            .map { it.toDomain() }
    }
    
    // ========== Entity <-> Domain 转换 ==========
    
    private fun AdviceItem.toEntity(): AdviceEntity {
        return AdviceEntity(
            id = id,
            category = category.name,
            priority = priority,
            title = title,
            content = content,
            triggerReason = triggerReason,
            createdAt = System.currentTimeMillis()
        )
    }
    
    private fun AdviceEntity.toDomain(): AdviceItem {
        return AdviceItem(
            id = id,
            category = AdviceCategory.valueOf(category),
            priority = priority,
            title = title,
            content = content,
            triggerReason = triggerReason
        )
    }
}
