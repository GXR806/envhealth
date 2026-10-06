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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
                val intent = Intent(context, EnvMonitorService::class.java).apply {
                    action = ACTION_START_MONITOR
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
    private var isMonitoring = false

    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 噪音监测器
    private var noiseMonitor: NoiseMonitor? = null
    
    // 光照监测器
    private var lightMonitor: LightMonitor? = null
    
    // 数据流订阅任务
    private var dataFlowJob: Job? = null
    private var lightDataFlowJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): EnvMonitorService = this@EnvMonitorService
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        LogX.i(TAG, "服务创建")
        
        // 初始化噪音监测器
        initializeNoiseMonitor()
        
        // 初始化光照监测器
        initializeLightMonitor()
    }

    /**
     * 初始化光照监测器
     */
    private fun initializeLightMonitor() {
        try {
            // 获取数据库实例
            val database = androidx.room.Room.databaseBuilder(
                applicationContext,
                EnvHealthDatabase::class.java,
                EnvHealthDatabase.DATABASE_NAME
            ).build()
            val lightDao = database.lightSampleDao()
            val lightRepository = com.GXR806.envhealth.data.repository.LightRepository(lightDao)

            // 创建光照监测器
            lightMonitor = LightMonitor(applicationContext, this, lightRepository)
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
            val database = androidx.room.Room.databaseBuilder(
                applicationContext,
                EnvHealthDatabase::class.java,
                EnvHealthDatabase.DATABASE_NAME
            ).build()
            val noiseDao = database.noiseSampleDao()
            val noiseRepository = NoiseRepository(noiseDao)

            // 获取校准偏移（从 DataStore）
            val settingsDataStore = SettingsDataStore(applicationContext)
            val calibrationOffset = runBlocking {
                settingsDataStore.dbCalibrationOffset.first()
            }

            // 创建噪音监测器
            noiseMonitor = NoiseMonitor(noiseRepository, calibrationOffset)
            LogX.i(TAG, "噪音监测器已初始化 (校准偏移: $calibrationOffset dB)")
        } catch (e: Exception) {
            LogX.e(TAG, "初始化噪音监测器失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_MONITOR -> {
                startMonitoring()
            }
            ACTION_STOP_MONITOR -> {
                stopMonitoring()
            }
        }
        return START_STICKY
    }

    /**
     * 启动监测
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            LogX.w(TAG, "监测已在运行中")
            return
        }

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

            // TODO: 启动音量监测（AudioManager）

            LogX.i(TAG, "监测服务已启动")
        } catch (e: Exception) {
            LogX.e(TAG, "启动监测失败", e)
            stopSelf()
        }
    }

    /**
     * 停止监测
     */
    private fun stopMonitoring() {
        if (!isMonitoring) {
            LogX.w(TAG, "监测未运行")
            stopSelf()
            return
        }

        try {
            // 停止数据流订阅
            dataFlowJob?.cancel()
            dataFlowJob = null
            lightDataFlowJob?.cancel()
            lightDataFlowJob = null
            
            // 停止噪音监测（需要在协程中执行以保存最后的数据）
            runBlocking {
                noiseMonitor?.stop()
            }
            
            // 停止光照监测
            lightMonitor?.stop()
            
            // 清空全局状态
            NoiseMonitorState.clear()
            LightMonitorState.clear()

            // TODO: 停止音量监测

            isMonitoring = false
            MonitorState.setMonitoring(false)

            // 停止前台服务
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()

            LogX.i(TAG, "监测服务已停止")
        } catch (e: Exception) {
            LogX.e(TAG, "停止监测失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 停止监测
        if (isMonitoring) {
            stopMonitoring()
        }
        
        // 取消协程作用域
        serviceScope.cancel()
        
        LogX.i(TAG, "服务销毁")
    }
}
