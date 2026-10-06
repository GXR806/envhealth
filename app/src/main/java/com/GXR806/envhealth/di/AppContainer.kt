package com.GXR806.envhealth.di

import android.content.Context
import androidx.room.Room
import com.GXR806.envhealth.data.local.room.EnvHealthDatabase
import com.GXR806.envhealth.data.repository.AdviceRepository
import com.GXR806.envhealth.data.repository.LightRepository
import com.GXR806.envhealth.data.repository.NoiseRepository
import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.analysis.AdviceEngine
import com.GXR806.envhealth.domain.analysis.SleepAnalyzer
import com.GXR806.envhealth.domain.sleep.SleepSessionManager
import com.GXR806.envhealth.domain.usecase.GenerateHealthAdviceUseCase
import com.GXR806.envhealth.domain.usecase.GenerateSleepReportUseCase

/**
 * 应用容器（手动依赖注入）
 * 
 * 管理应用级别的单例依赖
 */
class AppContainer(private val context: Context) {
    
    // Database
    private val database: EnvHealthDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            EnvHealthDatabase::class.java,
            EnvHealthDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    
    // DAOs
    private val noiseDao by lazy { database.noiseSampleDao() }
    private val lightDao by lazy { database.lightSampleDao() }
    private val sleepSessionDao by lazy { database.sleepSessionDao() }
    private val sleepReportDao by lazy { database.sleepReportDao() }
    private val adviceDao by lazy { database.adviceDao() }
    
    // Repositories
    val headphoneRepository by lazy {
        com.GXR806.envhealth.data.repository.HeadphoneRepository(database.headphoneUsageDao())
    }
    val headphoneGuard by lazy {
        com.GXR806.envhealth.data.sensor.volume.HeadphoneGuard(context, headphoneRepository)
    }
    val noiseRepository: NoiseRepository by lazy {
        NoiseRepository(noiseDao)
    }
    
    val lightRepository: LightRepository by lazy {
        LightRepository(lightDao)
    }
    
    val sleepRepository: SleepRepository by lazy {
        SleepRepository(sleepSessionDao, sleepReportDao)
    }
    
    val adviceRepository: AdviceRepository by lazy {
        AdviceRepository(adviceDao)
    }
    
    // Domain Layer
    val sleepSessionManager: SleepSessionManager by lazy {
        SleepSessionManager(sleepRepository)
    }
    
    val sleepAnalyzer: SleepAnalyzer by lazy {
        SleepAnalyzer(noiseRepository, lightRepository, sleepRepository)
    }
    
    val adviceEngine: AdviceEngine by lazy {
        AdviceEngine(adviceRepository)
    }
    
    // Use Cases
    val generateSleepReportUseCase: GenerateSleepReportUseCase by lazy {
        GenerateSleepReportUseCase(sleepAnalyzer, sleepRepository)
    }
    
    val generateHealthAdviceUseCase: GenerateHealthAdviceUseCase by lazy {
        GenerateHealthAdviceUseCase(adviceEngine, adviceRepository)
    }
}
