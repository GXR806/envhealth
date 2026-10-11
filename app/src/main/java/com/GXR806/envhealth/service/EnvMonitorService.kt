package com.GXR806.envhealth.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.GXR806.envhealth.data.local.room.EnvHealthDatabase
import com.GXR806.envhealth.data.local.datastore.SettingsDataStore
import com.GXR806.envhealth.data.repository.NoiseRepository
import com.GXR806.envhealth.data.repository.LightRepository
import com.GXR806.envhealth.data.sensor.audio.NoiseMonitor
import com.GXR806.envhealth.data.sensor.light.LightMonitor
import com.GXR806.envhealth.notification.NotificationHelper
import com.GXR806.envhealth.util.Constants
import com.GXR806.envhealth.util.LogX
import com.GXR806.envhealth.util.MonitorState
import com.GXR806.envhealth.util.NoiseMonitorState
import com.GXR806.envhealth.util.LightMonitorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 环境监测前台服务
 *
 * 负责长时运行的环境监测任务（噪音、光照、音量）
 * 使用前台服务确保系统不会随意杀死监测任务
 *
 * Android 版本兼容性：
 * - Android 12 (API 31+): 前台服务启动限制，需从可见 Activity 启动
 * - Android 13 (API 33+): 需要 POST_NOTIFICATIONS 权限
 * - Android 14 (API 34+): 需要声明 foregroundServiceType
 */
class EnvMonitorService : LifecycleService() {

    companion object {
        private const val TAG = "EnvMonitorService"
        private var active: EnvMonitorService? = null
        private var finishing: Job? = null
        fun requestReminderCheck(): Boolean {
            val service = active ?: return false
            if (!service.isMonitoring || service.isStopping || service.isDestroyed) return false
            return service.reminderRequests.tryEmit(Unit)
        }
        fun requestStopForFailure() {
            android.os.Handler(android.os.Looper.getMainLooper()).post { active?.stopMonitoring() }
        }
        suspend fun stopAndAwait() = kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
            active?.stopMonitoring()
            finishing?.join()
        }

        /** Action: 启动监测 */
        const val ACTION_START_MONITOR = "com.GXR806.envhealth.action.START_MONITOR"

        /** Action: 停止监测 */
        const val ACTION_STOP_MONITOR = "com.GXR806.envhealth.action.STOP_MONITOR"

        /**
         * 启动监测服务
         *
         * @param context 上下文
         * @return 是否成功启动（Android 12+ 可能因后台启动限制失败）
         */
        fun startMonitoring(context: Context): Boolean {
            return try {
                val container = (context.applicationContext as com.GXR806.envhealth.EnvHealthApplication).appContainer
                if (container.maintenanceGate.blocked.value || !container.consentReady.value) return false
                val intent = Intent(context, EnvMonitorService::class.java).apply {
                    action = ACTION_START_MONITOR
                    putExtra("generation", container.maintenanceGate.generation.value)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 12+ 前台服务启动限制：
                    // 只能在以下情况启动：
                    // 1. 应用在前台
                    // 2. 应用有 SYSTEM_ALERT_WINDOW 权限
                    // 3. 应用刚收到通知点击
                    // 4. 应用有其他特殊权限
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                LogX.i(TAG, "请求启动监测服务")
                true
            } catch (e: Exception) {
                LogX.e(TAG, "启动监测服务失败", e)
                false
            }
        }

        /**
         * 停止监测服务
         */
        fun stopMonitoring(context: Context) {
            val intent = Intent(context, EnvMonitorService::class.java).apply {
                action = ACTION_STOP_MONITOR
            }
            context.startService(intent)
            LogX.i(TAG, "请求停止监测服务")
        }
    }

    private val binder = LocalBinder()
    @Volatile private var isMonitoring = false
    private var isStarting = false
    @Volatile private var isStopping = false
    @Volatile private var isDestroyed = false
    private var cleanupJob: Job? = null
    private val headphoneGuard by lazy {
        (application as com.GXR806.envhealth.EnvHealthApplication).appContainer.headphoneGuard
    }

    // 协程作用域
    private val exceptions = kotlinx.coroutines.CoroutineExceptionHandler { _, failure ->
        if (failure is Error) {
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), failure)
            throw failure
        }
        MonitorState.reportError("监测任务异常，已请求停止；请手动重试。")
        LogX.e(TAG, "监测任务异常")
        // 系统主线程队列异步请求既有cleanup owner；handler不join、不阻塞。
        android.os.Handler(android.os.Looper.getMainLooper()).post { stopMonitoring() }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptions)

    // 噪音监测器
    private var noiseMonitor: NoiseMonitor? = null
    
    // 光照监测器
    private var lightMonitor: LightMonitor? = null
    
    // 数据流订阅任务
    private var dataFlowJob: Job? = null
    private var lightDataFlowJob: Job? = null
    private var reminderJob: Job? = null
    private val reminderRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    inner class LocalBinder : Binder() {
        fun getService(): EnvMonitorService = this@EnvMonitorService
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        active = this
        LogX.i(TAG, "服务创建")
        
        // 初始化噪音监测器
        initializeNoiseMonitor()
        
        // 初始化光照监测器
        initializeLightMonitor()
        serviceScope.launch(Dispatchers.Main.immediate) {
            var wasEnabled: Boolean? = null
            (application as com.GXR806.envhealth.EnvHealthApplication).appContainer.settingsRepository.snapshot.collect {
                noiseMonitor?.updateCalibrationOffset(it.dbCalibrationOffset)
                if ((wasEnabled == true && !it.backgroundMonitoringEnabled) || !it.hasPrivacyConsent || it.maintenanceState.isNotEmpty()) stopMonitoring()
                wasEnabled = it.backgroundMonitoringEnabled
            }
        }
    }

    /**
     * 初始化光照监测器
     */
    private fun initializeLightMonitor() {
        try {
            // 获取数据库实例
            val lightRepository = (application as com.GXR806.envhealth.EnvHealthApplication).appContainer.lightRepository

            // 创建光照监测器
            lightMonitor = LightMonitor(applicationContext, this, lightRepository,
                (application as com.GXR806.envhealth.EnvHealthApplication).appContainer.lightBatch)
            LogX.i(TAG, "光照监测器已初始化")
        } catch (e: Exception) {
            LogX.e(TAG, "初始化光照监测器失败", e)
        }
    }
    
    /**
     * 初始化噪音监测器
     */
    private fun initializeNoiseMonitor() {
        try {
            // 获取数据库实例
            val noiseRepository = (application as com.GXR806.envhealth.EnvHealthApplication).appContainer.noiseRepository

            // 默认96f，随后 settings snapshot 收集更新；不在主线程阻塞 DataStore IO。
            val calibrationOffset = SettingsDataStore.DEFAULT_DB_CALIBRATION_OFFSET

            // 创建噪音监测器
            noiseMonitor = NoiseMonitor(noiseRepository, calibrationOffset,
                (application as com.GXR806.envhealth.EnvHealthApplication).appContainer.noiseBatch)
            LogX.i(TAG, "噪音监测器已初始化 (校准偏移: $calibrationOffset dB)")
        } catch (e: Exception) {
            LogX.e(TAG, "初始化噪音监测器失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_MONITOR -> {
                val container = (application as com.GXR806.envhealth.EnvHealthApplication).appContainer
                if (intent.getLongExtra("generation", -1L) != container.maintenanceGate.generation.value || container.maintenanceGate.blocked.value) {
                    stopSelf()
                } else startMonitoring()
            }
            ACTION_STOP_MONITOR -> {
                stopMonitoring()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 启动监测
     */
    private fun startMonitoring() {
        if ((application as com.GXR806.envhealth.EnvHealthApplication).appContainer.maintenanceGate.blocked.value || finishing?.isActive == true) {
            stopSelf()
            return
        }
        if (isMonitoring || isStarting || isStopping || isDestroyed) {
            LogX.w(TAG, "监测已在运行中")
            return
        }

        isStarting = true
        try {
            // 创建停止监测的 PendingIntent
            val stopIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, EnvMonitorService::class.java).apply {
                    action = ACTION_STOP_MONITOR
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 构建前台服务通知
            val notification = NotificationHelper.buildForegroundServiceNotification(
                context = this,
                isMonitoring = true,
                stopIntent = stopIntent
            )

            // 启动前台服务
            // Android 14 (API 34+) 需要声明 foregroundServiceType
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NotificationId.FOREGROUND_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NotificationId.FOREGROUND_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else {
                startForeground(
                    NotificationHelper.NotificationId.FOREGROUND_SERVICE,
                    notification
                )
            }

            // 前台通知先满足系统期限；读取授权后再开始硬件采集。
            serviceScope.launch(Dispatchers.Main.immediate) {
                val container = (application as com.GXR806.envhealth.EnvHealthApplication).appContainer
                try {
                    val settings = container.settingsRepository.snapshot.first()
                    if (!settings.hasPrivacyConsent || !settings.backgroundMonitoringEnabled || settings.maintenanceState.isNotEmpty() || container.maintenanceGate.blocked.value || isStopping || isDestroyed) {
                        stopMonitoring()
                        return@launch
                    }
                    // 只在明确手动启动时重试旧有界批次，失败不开硬件。
                    container.noiseBatch.flush()
                    container.lightBatch.flush()
                    if (isStopping || isDestroyed || container.maintenanceGate.blocked.value) return@launch
                    beginCollection()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { LogX.e(TAG, "授权读取失败", e); stopMonitoring() }
            }
        } catch (e: Exception) {
            LogX.e(TAG, "启动监测失败", e)
            stopMonitoring()
        }
    }

    private fun beginCollection() {
        try {
            MonitorState.clearError()
            isStarting = false
            isMonitoring = true
            MonitorState.setMonitoring(true)

            // 启动噪音监测
            val started = noiseMonitor?.start(serviceScope) ?: false
            if (!started) {
                LogX.e(TAG, "启动噪音监测失败")
                stopMonitoring()
                return
            }
            
            // 订阅噪音监测数据流，更新全局状态供 UI 订阅
            dataFlowJob = serviceScope.launch {
                noiseMonitor?.analysisResultFlow?.collect { result ->
                    // 更新全局状态供 UI 订阅
                    NoiseMonitorState.updateAnalysisResult(result)
                    // LogX.d(TAG, "噪音数据更新: ${result?.currentDb} dB")  // 日志已禁用：过于频繁
                }
            }

            // 启动光照监测
            val lightStarted = lightMonitor?.start(serviceScope) ?: false
            if (!lightStarted) {
                LogX.w(TAG, "启动光照监测失败（可能相机不可用）")
            } else {
                // 订阅光照监测数据流
                lightDataFlowJob = serviceScope.launch {
                    lightMonitor?.lightFrameFlow?.collect { frame ->
                        LightMonitorState.updateLightFrame(frame)
                        // LogX.d(TAG, "光照数据更新: ${frame?.estimatedLux} lux, 亮度: ${frame?.relativeBrightness}%")  // 日志已禁用：过于频繁
                    }
                }
            }

            headphoneGuard.start()
            startRealtimeReminders()

            LogX.i(TAG, "监测服务已启动")
        } catch (e: Exception) {
            LogX.e(TAG, "启动监测失败", e)
            stopSelf()
        }
    }

    /** Noise is read only on a 30s tick; headphone changes and settings trigger immediately.
     * Ignore reading timestamps so OEM 5s volume polls do not cause redundant lock/IO.
     * Duration/weekly use HeadphoneGuard's existing running aggregates (no extra history query).
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startRealtimeReminders() {
        val container = (application as com.GXR806.envhealth.EnvHealthApplication).appContainer
        val generation = container.maintenanceGate.generation.value
        reminderJob = serviceScope.launch(Dispatchers.IO) {
            val coordinator = com.GXR806.envhealth.worker.ReminderCoordinator(container.reminderRuntime)
            val tick = flow {
                while (currentCoroutineContext().isActive) {
                    emit(Unit)
                    delay(30_000)
                }
            }
            val noise = NoiseMonitorState.analysisResult.sample(30_000)
                .onStart { emit(NoiseMonitorState.analysisResult.value) }
            val triggers = combine(merge(tick, reminderRequests), noise, headphoneGuard.state.map {
                Triple(it.running, it.reading?.percent, Pair(it.reading?.device, it.reading?.playing))
            }.distinctUntilChanged(), container.settingsRepository.snapshot) { _, _, _, _ -> Unit }
            com.GXR806.envhealth.worker.RealtimeReminderListener().run(triggers, check = {
                coordinator.run(remindOnly = true) {
                    isMonitoring && !isStopping && !isDestroyed &&
                        generation == container.maintenanceGate.generation.value &&
                        !container.maintenanceGate.blocked.value
                }
            }, failure = {
                container.reminderRuntime.diagnostic(null, "实时提醒检查失败，保留节流；监听继续重试")
                LogX.e(TAG, "实时提醒检查失败，监听继续重试")
            })
        }
    }

    /**
     * 停止监测
     */
    private fun stopMonitoring() {
        if (isStopping || cleanupJob != null) return
        isStopping = true
        reminderJob?.cancel() // invalidate before any cleanup suspension / gate wait
        isStarting = false
        // 明确拥有的短生命周期作用域，不作为 serviceScope 的子任务。
        // onDestroy 取消采集任务不会取消最终落库，完成后自动释放该作用域。
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptions)
        cleanupJob = cleanupScope.launch {
            try {
                val uiJobs = listOfNotNull(dataFlowJob, lightDataFlowJob, reminderJob)
                uiJobs.forEach { it.cancel() }
                reminderJob = null
                dataFlowJob = null
                lightDataFlowJob = null
                val headphoneDone = try {
                    headphoneGuard.stop() // Main 上立即注销，不等待数据库
                } catch (e: Exception) {
                    LogX.e(TAG, "停止耳机监听失败", e)
                    null
                }
                try {
                    // CameraX unbindAll 必须 Main；在任何 IO 等待前先停止相机。
                    lightMonitor?.stop()
                    uiJobs.forEach { it.join() }
                    lightMonitor?.awaitStopped()
                } catch (e: Exception) {
                    LogX.e(TAG, "停止光照监测失败", e)
                } finally {
                    try {
                        noiseMonitor?.stop() // suspend，绝不 runBlocking
                    } catch (e: Exception) {
                        LogX.e(TAG, "停止噪音监测失败", e)
                    } finally {
                        try {
                            headphoneDone?.await()
                        } catch (e: Exception) {
                            LogX.e(TAG, "耳机最终落库失败", e)
                        }
                    }
                }
            } finally {
                NoiseMonitorState.clear()
                LightMonitorState.clear()
                isMonitoring = false
                MonitorState.setMonitoring(false)
                try {
                    ServiceCompat.stopForeground(this@EnvMonitorService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                } finally {
                    if (!isDestroyed) stopSelf()
                    serviceScope.cancel()
                    cleanupScope.cancel()
                }
                LogX.i(TAG, "监测服务已停止")
            }
        }
        finishing = cleanupJob
    }

    override fun onDestroy() {
        isDestroyed = true
        if (active === this) active = null
        // Main.immediate 立即注销耳机和解绑相机，IO 释放录音/最终存储独立继续。
        try {
            stopMonitoring()
        } finally {
            serviceScope.cancel()
            NoiseMonitorState.clear()
            LightMonitorState.clear()
            isMonitoring = false
            MonitorState.setMonitoring(false)
            super.onDestroy()
        }
        LogX.i(TAG, "服务销毁，异步清理继续完成")
    }
}
