package com.GXR806.envhealth.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.withLock
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
    // Process-owned scope: no Activity ownership and no GlobalScope; cancel in close()/Application.onTerminate.
    private val exceptions = kotlinx.coroutines.CoroutineExceptionHandler { _, failure ->
        if (failure is Error) {
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), failure)
            throw failure
        }
        maintenanceGate.blocked.value = true
        consentReady.value = false
        android.util.Log.e("EnvHealth", "应用任务失败，入口保持关闭")
    }
    private val owner = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO + exceptions)
    val consentReady = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun initializePrivacy() = owner.launch {
        try {
            maintenanceGate.mutex.withLock {
                val settings = settingsRepository.snapshot.first()
                maintenanceGate.blocked.value = settings.maintenanceState.isNotEmpty()
                maintenanceGate.generation.value += 1
                consentReady.value = settings.hasPrivacyConsent
                if (settings.hasPrivacyConsent && settings.maintenanceState.isEmpty())
                    com.GXR806.envhealth.worker.ReminderScheduler.restore(context)
                else com.GXR806.envhealth.worker.ReminderScheduler.cancel(context).result.get()
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("Privacy", "隐私状态读取失败，保持关闭", e) }
    }
    fun restoreConsentedWork() { owner.launch {
        try { maintenanceGate.mutex.withLock {
            val settings = settingsRepository.snapshot.first()
            consentReady.value = settings.hasPrivacyConsent
            if (!maintenanceGate.blocked.value && settings.hasPrivacyConsent && settings.maintenanceState.isEmpty())
                com.GXR806.envhealth.worker.ReminderScheduler.restore(context)
        } } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("Privacy", "周期恢复失败", e) }
    } }
    fun close() { owner.cancel() }

    
    val settingsRepository by lazy { com.GXR806.envhealth.data.repository.SettingsRepository(context) }
    val maintenanceGate = com.GXR806.envhealth.worker.MaintenanceGate()
    val reminderDiagnostics = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val reminderRuntime by lazy { object : com.GXR806.envhealth.worker.ReminderRuntime {
        override fun diagnostic(type: com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type?, reason: String) {
            val key = type?.name ?: "ALL"
            val time = java.time.Instant.ofEpochMilli(now()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            reminderDiagnostics.value = reminderDiagnostics.value + (key to "$time：$reason")
        }
        override val gate get() = maintenanceGate
        override val store get() = settingsRepository.reminderStore
        override val sink = object : com.GXR806.envhealth.worker.ReminderNotificationSink {
            override suspend fun eligible(event: com.GXR806.envhealth.domain.analysis.ReminderPolicy.Event) =
                com.GXR806.envhealth.notification.NotificationHelper.canSendReminder(context, event)
            override suspend fun send(event: com.GXR806.envhealth.domain.analysis.ReminderPolicy.Event) =
                com.GXR806.envhealth.notification.NotificationHelper.sendReminder(context, event)
        }
        override suspend fun settings() = settingsRepository.snapshot.first()
        override suspend fun risk(now: Long): com.GXR806.envhealth.domain.analysis.ReminderPolicy.LiveRisk {
            val noise = com.GXR806.envhealth.util.NoiseMonitorState.analysisResult.value
            val headphone = headphoneGuard.state.value
            val reading = headphone.reading
            val week = if (headphone.running) headphone.weekMs else
                com.GXR806.envhealth.domain.analysis.clippedDuration(headphoneRepository.getCompletedIntervals(),
                    now - 7 * com.GXR806.envhealth.domain.analysis.ReminderPolicy.DAY_MS, now)
            return com.GXR806.envhealth.domain.analysis.ReminderPolicy.LiveRisk(
                noise?.currentDb, noise?.timestamp, reading?.percent, reading?.wall,
                headphone.running && reading?.device != null && reading.playing,
                headphone.durationMs, week)
        }
        override suspend fun cleanup(now: Long) {
            val settings = settingsRepository.snapshot.first()
            if (!com.GXR806.envhealth.domain.analysis.RetentionPolicy.cleanupDue(now,
                    settings.lastCleanupTimestamp.takeIf { it != 0L })) return
            database.withTransaction {
                noiseDao.deleteBeforeTime(now - com.GXR806.envhealth.domain.analysis.RetentionPolicy.SAMPLE_MS)
                lightDao.deleteBeforeTime(now - com.GXR806.envhealth.domain.analysis.RetentionPolicy.SAMPLE_MS)
                sleepSessionDao.deleteCompletedBeforeEnd(now - com.GXR806.envhealth.domain.analysis.RetentionPolicy.AGGREGATE_MS)
                database.headphoneUsageDao().deleteCompletedBeforeEnd(now - com.GXR806.envhealth.domain.analysis.RetentionPolicy.AGGREGATE_MS)
                adviceDao.deleteBeforeTime(now - com.GXR806.envhealth.domain.analysis.RetentionPolicy.AGGREGATE_MS)
            }
            settingsRepository.setLastCleanupTimestamp(now)
        }
    } }

    val maintenance by lazy { com.GXR806.envhealth.worker.DataMaintenance(context, this) }
    suspend fun clearDatabase() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        database.clearAllTables()
    }

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
    
    // 进程拥有的小批次：Service销毁不丢失败待写；清除在stop/join后显式discard。
    val noiseBatch by lazy { com.GXR806.envhealth.data.sensor.BoundedSampleBatch<com.GXR806.envhealth.domain.model.NoiseSample>(
        { android.os.SystemClock.elapsedRealtime() }, noiseRepository::insertSamples) }
    val lightBatch by lazy { com.GXR806.envhealth.data.sensor.BoundedSampleBatch<com.GXR806.envhealth.domain.model.LightSample>(
        { android.os.SystemClock.elapsedRealtime() }, lightRepository::insertSamples) }
    val lightRepository: LightRepository by lazy {
        LightRepository(lightDao)
    }
    
    val sleepRepository: SleepRepository by lazy {
        SleepRepository(sleepSessionDao, sleepReportDao)
    }
    
    val adviceRepository: AdviceRepository by lazy {
        AdviceRepository(adviceDao)
    }
    
    val historyRepository by lazy {
        com.GXR806.envhealth.data.repository.HistoryRepository(noiseRepository, lightRepository, headphoneRepository, sleepRepository)
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
