package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.GXR806.envhealth.data.local.room.entity.AdviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 健康建议数据访问对象
 */
@Dao
interface AdviceDao {
    
    /**
     * 插入建议
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(advice: AdviceEntity): Long
    
    /**
     * 批量插入建议
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(adviceList: List<AdviceEntity>): List<Long>
    
    /**
     * 查询所有建议（按优先级倒序，创建时间倒序）
     */
    @Query("SELECT * FROM advice_items ORDER BY priority DESC, createdAt DESC")
    fun getAllAdvice(): Flow<List<AdviceEntity>>
    
    /**
     * 按类别查询建议
     */
    @Query("SELECT * FROM advice_items WHERE category = :category ORDER BY priority DESC, createdAt DESC")
    fun getAdviceByCategory(category: String): Flow<List<AdviceEntity>>
    
    /**
     * 查询最近 N 条建议
     */
    @Query("SELECT * FROM advice_items ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentAdvice(limit: Int): Flow<List<AdviceEntity>>
    
    /**
     * 根据 ID 查询建议
     */
    @Query("SELECT * FROM advice_items WHERE id = :adviceId")
    suspend fun getById(adviceId: Long): AdviceEntity?
    
    /**
     * 删除指定 ID 的建议
     */
    @Query("DELETE FROM advice_items WHERE id = :adviceId")
    suspend fun deleteById(adviceId: Long): Int
    
    /**
     * 删除指定时间之前的数据（数据保留策略：90 天）
     */
    @Query("DELETE FROM advice_items WHERE createdAt < :beforeTime")
    suspend fun deleteBeforeTime(beforeTime: Long): Int
    
    /**
     * 删除全部数据
     */
    @Query("DELETE FROM advice_items")
    suspend fun deleteAll(): Int
}
