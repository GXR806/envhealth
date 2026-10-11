package com.GXR806.envhealth.data.local.room.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.GXR806.envhealth.data.local.room.EnvHealthDatabase
import com.GXR806.envhealth.data.local.room.entity.LightSampleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * 光照采样 DAO 单元测试
 * 使用 Room in-memory 数据库进行测试
 */
class LightSampleDaoTest {
    
    private lateinit var database: EnvHealthDatabase
    private lateinit var lightSampleDao: LightSampleDao
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EnvHealthDatabase::class.java
        ).allowMainThreadQueries().build()
        
        lightSampleDao = database.lightSampleDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertSample_andRetrieve_success() = runBlocking {
        // Given
        val sample = LightSampleEntity(
            timestamp = 1000L,
            avgLuma = 128.5f,
            estimatedLux = 250f,
            level = "NORMAL_INDOOR"
        )
        
        // When
        val insertedId = lightSampleDao.insert(sample)
        val count = lightSampleDao.getCount()
        
        // Then
        assertTrue(insertedId > 0)
        assertEquals(1, count)
    }
    
    @Test
    fun insertMultipleSamples_andQueryByTimeRange_success() = runBlocking {
        // Given
        val samples = listOf(
            LightSampleEntity(timestamp = 1000L, avgLuma = 50f, estimatedLux = 100f, level = "DIM"),
            LightSampleEntity(timestamp = 2000L, avgLuma = 128f, estimatedLux = 250f, level = "NORMAL_INDOOR"),
            LightSampleEntity(timestamp = 3000L, avgLuma = 200f, estimatedLux = 400f, level = "BRIGHT_INDOOR")
        )
        
        // When
        lightSampleDao.insertAll(samples)
        val result = lightSampleDao.getSamplesByTimeRange(1500L, 3500L).first()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(2000L, result[0].timestamp)
        assertEquals(3000L, result[1].timestamp)
    }
    
    @Test
    fun getAverageLuma_returnsCorrectValue() = runBlocking {
        // Given
        val samples = listOf(
            LightSampleEntity(timestamp = 1000L, avgLuma = 100f, estimatedLux = 200f, level = "DIM"),
            LightSampleEntity(timestamp = 2000L, avgLuma = 200f, estimatedLux = 400f, level = "BRIGHT_INDOOR")
        )
        lightSampleDao.insertAll(samples)
        
        // When
        val avgLuma = lightSampleDao.getAverageLuma(0L, 3000L)
        
        // Then
        assertNotNull(avgLuma)
        assertEquals(150f, avgLuma!!, 0.01f)
    }
    
    @Test
    fun getAverageLux_returnsCorrectValue() = runBlocking {
        // Given
        val samples = listOf(
            LightSampleEntity(timestamp = 1000L, avgLuma = 100f, estimatedLux = 200f, level = "DIM"),
            LightSampleEntity(timestamp = 2000L, avgLuma = 200f, estimatedLux = 400f, level = "BRIGHT_INDOOR")
        )
        lightSampleDao.insertAll(samples)
        
        // When
        val avgLux = lightSampleDao.getAverageLux(0L, 3000L)
        
        // Then
        assertNotNull(avgLux)
        assertEquals(300f, avgLux!!, 0.01f)
    }
    
    @Test
    fun deleteBeforeTime_removesOldData() = runBlocking {
        // Given
        val samples = listOf(
            LightSampleEntity(timestamp = 1000L, avgLuma = 100f, estimatedLux = 200f, level = "DIM"),
            LightSampleEntity(timestamp = 2000L, avgLuma = 128f, estimatedLux = 250f, level = "NORMAL_INDOOR"),
            LightSampleEntity(timestamp = 3000L, avgLuma = 200f, estimatedLux = 400f, level = "BRIGHT_INDOOR")
        )
        lightSampleDao.insertAll(samples)
        
        // When
        val deletedCount = lightSampleDao.deleteBeforeTime(2500L)
        val remainingCount = lightSampleDao.getCount()
        
        // Then
        assertEquals(2, deletedCount)
        assertEquals(1, remainingCount)
    }
}
