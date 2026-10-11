package com.GXR806.envhealth.worker

import android.content.Context
import com.GXR806.envhealth.di.AppContainer
import com.GXR806.envhealth.notification.NotificationHelper
import com.GXR806.envhealth.service.EnvMonitorService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 测试替换边界，不建立第二套生产 DI，不访问生产存储。 */
interface MaintenanceOperations {
    val gate: MaintenanceGate
    suspend fun state(value: String)
    suspend fun cancelWork()
    suspend fun stopWriters()
    suspend fun deleteDatabase()
    suspend fun clearCaches()
    suspend fun resetSettings()
    suspend fun background(enabled: Boolean)
    fun cancelNotifications()
    fun restoreCleanupWork()
}

/** 两阶段清除：先封入口/停止并 join，再在维护锁下删除。绝不持写锁等待停止。 */
class DataMaintenance(private val operations: MaintenanceOperations) {
    constructor(context: Context, container: AppContainer) : this(object : MaintenanceOperations {
        override val gate = container.maintenanceGate
        override suspend fun state(value: String) = container.settingsRepository.setMaintenanceState(value)
        override suspend fun cancelWork() = withContext(Dispatchers.IO) { ReminderScheduler.cancel(context).result.get(); Unit }
        override suspend fun stopWriters() {
            EnvMonitorService.stopAndAwait()
            withContext(Dispatchers.Main.immediate) { container.headphoneGuard.stop().await() }
        }
        override suspend fun deleteDatabase() = container.clearDatabase()
        override suspend fun clearCaches() {
            container.reminderDiagnostics.value = emptyMap()
            container.noiseBatch.discard()
            container.lightBatch.discard()
            com.GXR806.envhealth.util.MonitorState.clearError()
            withContext(Dispatchers.Main.immediate) { container.headphoneGuard.clearStoppedCache() }
            com.GXR806.envhealth.util.NoiseMonitorState.clear()
            com.GXR806.envhealth.util.LightMonitorState.clear()
        }
        override suspend fun resetSettings() {
            container.consentReady.value = false
            container.settingsRepository.resetAll()
        }
        override suspend fun background(enabled: Boolean) = container.settingsRepository.setBackgroundMonitoringEnabled(enabled)
        override fun cancelNotifications() = NotificationHelper.cancelReminders(context)
        override fun restoreCleanupWork() = container.restoreConsentedWork()
    })
    private val clearMutex = Mutex()
    suspend fun clearAll() = clearMutex.withLock {
        val gate = operations.gate
        withContext(NonCancellable) {
            gate.blocked.value = true
            try {
                gate.mutex.withLock {
                    gate.blocked.value = true
                    operations.state("clearing")
                }
                operations.cancelWork()
                operations.stopWriters()
                gate.mutex.withLock {
                    operations.deleteDatabase()
                    operations.clearCaches()
                    operations.cancelNotifications()
                    operations.resetSettings()
                    gate.generation.value += 1
                    gate.blocked.value = false
                }
                operations.restoreCleanupWork()
            } catch (e: Exception) {
                gate.blocked.value = true
                runCatching { operations.state("error") }
                throw e
            }
        }
    }
    suspend fun setBackground(enabled: Boolean) = clearMutex.withLock {
        operations.gate.write { operations.background(enabled) }
        if (!enabled) {
            operations.stopWriters()
            operations.cancelNotifications()
        }
        operations.restoreCleanupWork()
    }
}
