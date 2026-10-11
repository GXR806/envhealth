package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.GXR806.envhealth.data.local.room.EnvHealthDatabase
import com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * 睡眠会话 DAO 单元测试
 * 使用 Room in-memory 数据库进行测试
 */
class SleepSessionDaoTest {
    
    private lateinit var database: EnvHealthDatabase
    private lateinit var sleepSessionDao: SleepSessionDao
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnvHealthDatabase::class.java
        ).allowMainThreadQueries().build()
        
        sleepSessionDao = database.sleepSessionDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertSession_andRetrieveById_success() = runBlocking {
        // Given
        val session = SleepSessionEntity(
            startTime = 1000L,
            endTime = null,
            type = "MANUAL",
            status = "ONGOING"
        )
        
        // When
        val insertedId = sleepSessionDao.insert(session)
        val retrieved = sleepSessionDao.getById(insertedId)
        
        // Then
        assertNotNull(retrieved)
        assertEquals(insertedId, retrieved!!.id)
        assertEquals("MANUAL", retrieved.type)
        assertEquals("ONGOING", retrieved.status)
    }
    
    @Test
    fun updateSession_success() = runBlocking {
        // Given
        val session = SleepSessionEntity(
            startTime = 1000L,
            endTime = null,
            type = "MANUAL",
            status = "ONGOING"
        )
        val insertedId = sleepSessionDao.insert(session)
        
        // When
        val updatedSession = session.copy(id = insertedId, endTime = 5000L, status = "COMPLETED")
        sleepSessionDao.update(updatedSession)
        val retrieved = sleepSessionDao.getById(insertedId)
        
        // Then
        assertNotNull(retrieved)
        assertEquals(5000L, retrieved!!.endTime)
        assertEquals("COMPLETED", retrieved.status)
    }
    
    @Test
    fun getOngoingSessions_returnsOnlyOngoing() = runBlocking {
        // Given
        val sessions = listOf(
            SleepSessionEntity(startTime = 1000L, endTime = null, type = "MANUAL", status = "ONGOING"),
            SleepSessionEntity(startTime = 2000L, endTime = 3000L, type = "AUTO", status = "COMPLETED"),
            SleepSessionEntity(startTime = 4000L, endTime = null, type = "MANUAL", status = "ONGOING")
        )
        sessions.forEach { sleepSessionDao.insert(it) }
        
        // When
        val ongoing = sleepSessionDao.getOngoingSessions().first()
        
        // Then
        assertEquals(2, ongoing.size)
        assertTrue(ongoing.all { it.status == "ONGOING" })
    }
    
    @Test
    fun getSessionsByTimeRange_success() = runBlocking {
        // Given
        val sessions = listOf(
            SleepSessionEntity(startTime = 1000L, endTime = 2000L, type = "MANUAL", status = "COMPLETED"),
            SleepSessionEntity(startTime = 3000L, endTime = 4000L, type = "AUTO", status = "COMPLETED"),
            SleepSessionEntity(startTime = 5000L, endTime = 6000L, type = "MANUAL", status = "COMPLETED")
        )
        sessions.forEach { sleepSessionDao.insert(it) }
        
        // When
        val result = sleepSessionDao.getSessionsByTimeRange(2500L, 5500L).first()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(3000L, result[1].startTime) // 倒序
        assertEquals(5000L, result[0].startTime)
    }
    
    @Test
    fun getCountByTimeRange_returnsCorrectValue() = runBlocking {
        // Given
        val sessions = listOf(
            SleepSessionEntity(startTime = 1000L, endTime = 2000L, type = "MANUAL", status = "COMPLETED"),
            SleepSessionEntity(startTime = 3000L, endTime = 4000L, type = "AUTO", status = "COMPLETED"),
            SleepSessionEntity(startTime = 5000L, endTime = 6000L, type = "MANUAL", status = "COMPLETED")
        )
        sessions.forEach { sleepSessionDao.insert(it) }
        
        // When
        val count = sleepSessionDao.getCountByTimeRange(2500L, 5500L)
        
        // Then
        assertEquals(2, count)
    }
    
    @Test
    fun deleteBeforeTime_removesOldData() = runBlocking {
        // Given
        val sessions = listOf(
            SleepSessionEntity(startTime = 1000L, endTime = 2000L, type = "MANUAL", status = "COMPLETED"),
            SleepSessionEntity(startTime = 3000L, endTime = 4000L, type = "AUTO", status = "COMPLETED"),
            SleepSessionEntity(startTime = 5000L, endTime = 6000L, type = "MANUAL", status = "COMPLETED")
        )
        sessions.forEach { sleepSessionDao.insert(it) }
        
        // When
        val deletedCount = sleepSessionDao.deleteBeforeTime(4500L)
        
        // Then
        assertEquals(2, deletedCount)
    }
}
