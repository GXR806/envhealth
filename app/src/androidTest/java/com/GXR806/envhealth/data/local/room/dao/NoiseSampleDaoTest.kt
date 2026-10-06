package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.GXR806.envhealth.data.local.room.EnvHealthDatabase
import com.GXR806.envhealth.data.local.room.entity.NoiseSampleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * 噪音采样 DAO 单元测试
 * 使用 Room in-memory 数据库进行测试
 */
class NoiseSampleDaoTest {
    
    private lateinit var database: EnvHealthDatabase
    private lateinit var noiseSampleDao: NoiseSampleDao
    
    @Before
    fun setup() {
        // 创建内存数据库
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnvHealthDatabase::class.java
        ).allowMainThreadQueries().build()
        
        noiseSampleDao = database.noiseSampleDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertSample_andRetrieve_success() = runTest {
        // Given
        val sample = NoiseSampleEntity(
            timestamp = 1000L,
            leqDb = 45.5f,
            maxDb = 50.0f,
            peakDb = 55.0f,
            isEvent = false
        )
        
        // When
        val insertedId = noiseSampleDao.insert(sample)
        val count = noiseSampleDao.getCount()
        
        // Then
        assertTrue(insertedId > 0)
        assertEquals(1, count)
    }
    
    @Test
    fun insertMultipleSamples_andQueryByTimeRange_success() = runTest {
        // Given
        val samples = listOf(
            NoiseSampleEntity(timestamp = 1000L, leqDb = 45f, maxDb = 50f, peakDb = 55f, isEvent = false),
            NoiseSampleEntity(timestamp = 2000L, leqDb = 60f, maxDb = 65f, peakDb = 70f, isEvent = false),
            NoiseSampleEntity(timestamp = 3000L, leqDb = 75f, maxDb = 80f, peakDb = 85f, isEvent = true)
        )
        
        // When
        noiseSampleDao.insertAll(samples)
        val result = noiseSampleDao.getSamplesByTimeRange(1500L, 3500L).first()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(2000L, result[0].timestamp)
        assertEquals(3000L, result[1].timestamp)
    }
    
    @Test
    fun getAverageDb_returnsCorrectValue() = runTest {
        // Given
        val samples = listOf(
            NoiseSampleEntity(timestamp = 1000L, leqDb = 40f, maxDb = 50f, peakDb = 55f, isEvent = false),
            NoiseSampleEntity(timestamp = 2000L, leqDb = 60f, maxDb = 65f, peakDb = 70f, isEvent = false)
        )
        noiseSampleDao.insertAll(samples)
        
        // When
        val avgDb = noiseSampleDao.getAverageDb(0L, 3000L)
        
        // Then
        assertNotNull(avgDb)
        assertEquals(50f, avgDb!!, 0.01f)
    }
    
    @Test
    fun getMaxDb_returnsCorrectValue() = runTest {
        // Given
        val samples = listOf(
            NoiseSampleEntity(timestamp = 1000L, leqDb = 40f, maxDb = 50f, peakDb = 55f, isEvent = false),
            NoiseSampleEntity(timestamp = 2000L, leqDb = 60f, maxDb = 80f, peakDb = 85f, isEvent = false)
        )
        noiseSampleDao.insertAll(samples)
        
        // When
        val maxDb = noiseSampleDao.getMaxDb(0L, 3000L)
        
        // Then
        assertNotNull(maxDb)
        assertEquals(80f, maxDb!!, 0.01f)
    }
    
    @Test
    fun getEventCount_returnsCorrectValue() = runTest {
        // Given
        val samples = listOf(
            NoiseSampleEntity(timestamp = 1000L, leqDb = 40f, maxDb = 50f, peakDb = 55f, isEvent = false),
            NoiseSampleEntity(timestamp = 2000L, leqDb = 75f, maxDb = 80f, peakDb = 85f, isEvent = true),
            NoiseSampleEntity(timestamp = 3000L, leqDb = 78f, maxDb = 83f, peakDb = 88f, isEvent = true)
        )
        noiseSampleDao.insertAll(samples)
        
        // When
        val eventCount = noiseSampleDao.getEventCount(0L, 4000L)
        
        // Then
        assertEquals(2, eventCount)
    }
    
    @Test
    fun deleteBeforeTime_removesOldData() = runTest {
        // Given
        val samples = listOf(
            NoiseSampleEntity(timestamp = 1000L, leqDb = 40f, maxDb = 50f, peakDb = 55f, isEvent = false),
            NoiseSampleEntity(timestamp = 2000L, leqDb = 60f, maxDb = 65f, peakDb = 70f, isEvent = false),
            NoiseSampleEntity(timestamp = 3000L, leqDb = 75f, maxDb = 80f, peakDb = 85f, isEvent = true)
        )
        noiseSampleDao.insertAll(samples)
        
        // When
        val deletedCount = noiseSampleDao.deleteBeforeTime(2500L)
        val remainingCount = noiseSampleDao.getCount()
        
        // Then
        assertEquals(2, deletedCount)
        assertEquals(1, remainingCount)
    }
    
    @Test
    fun deleteAll_removesAllData() = runTest {
        // Given
        val samples = listOf(
            NoiseSampleEntity(timestamp = 1000L, leqDb = 40f, maxDb = 50f, peakDb = 55f, isEvent = false),
            NoiseSampleEntity(timestamp = 2000L, leqDb = 60f, maxDb = 65f, peakDb = 70f, isEvent = false)
        )
        noiseSampleDao.insertAll(samples)
        
        // When
        noiseSampleDao.deleteAll()
        val count = noiseSampleDao.getCount()
        
        // Then
        assertEquals(0, count)
    }
}
