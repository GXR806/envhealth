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
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            // 不执行IO/等待资源释放，不启动新线程，尤其OOM时不创建协程或拼接堆栈。
            try {
                android.util.Log.e("EnvHealth", "未捕获异常，交由系统终止")
                if (failure !is Error) com.GXR806.envhealth.service.EnvMonitorService.requestStopForFailure()
            }
            finally {
                if (systemHandler != null) systemHandler.uncaughtException(thread, failure)
                else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
        appContainer = AppContainer(this)
        // 冷启动先读持久维护态；读取完成前禁止写入/采集。
        appContainer.maintenanceGate.blocked.value = true
        appContainer.initializePrivacy()
        com.GXR806.envhealth.notification.NotificationHelper.createNotificationChannels(this)
    }

    override fun onTerminate() {
        appContainer.close()
        super.onTerminate()
    }
}
