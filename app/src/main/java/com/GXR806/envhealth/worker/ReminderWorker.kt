package com.GXR806.envhealth.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.GXR806.envhealth.EnvHealthApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** 永远只读风险/清理，不启动 microphone/camera；后台关闭时同一个工作仅执行清理。 */
class ReminderWorker @JvmOverloads constructor(context: Context, parameters: WorkerParameters,
    private val injectedRuntime: ReminderRuntime? = null) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        ReminderCoordinator(injectedRuntime ?: (applicationContext as EnvHealthApplication).appContainer.reminderRuntime).run()
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.retry() }
}

object ReminderScheduler {
    const val UNIQUE_NAME = "health_reminder_and_retention_v1"
    suspend fun restoreIfConsented(context: Context) {
        val settings = com.GXR806.envhealth.data.repository.SettingsRepository(context).snapshot.first()
        if (settings.hasPrivacyConsent && settings.maintenanceState.isEmpty()) restore(context)
        else cancel(context)
    }
    fun restore(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build())
    }
    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
}
