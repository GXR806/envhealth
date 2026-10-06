package com.GXR806.envhealth

import android.app.Application
import com.GXR806.envhealth.di.AppContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/**
 * 应用程序类
 * 
 * 负责初始化应用级别的单例依赖
 */
class EnvHealthApplication : Application() {
    
    lateinit var appContainer: AppContainer
        private set
    
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        // 冷启动先读持久维护态；读取完成前禁止写入/采集。
        appContainer.maintenanceGate.blocked.value = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).let { owner ->
            owner.launch {
                try {
                    appContainer.maintenanceGate.mutex.withLock {
                        appContainer.maintenanceGate.blocked.value = appContainer.settingsRepository.snapshot.first().maintenanceState.isNotEmpty()
                        appContainer.maintenanceGate.generation.value += 1
                    }
                } finally { owner.cancel() }
            }
        }
        com.GXR806.envhealth.notification.NotificationHelper.createNotificationChannels(this)
        com.GXR806.envhealth.worker.ReminderScheduler.restore(this)
    }
}
