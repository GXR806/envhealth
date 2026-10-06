package com.GXR806.envhealth.data.sensor.light

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.GXR806.envhealth.data.repository.LightRepository
import com.GXR806.envhealth.domain.model.LightSample
import com.GXR806.envhealth.util.LogX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 光照监测协调器
 * 
 * 整合 LightSensorCamera，提供：
 * 1. 实时光照数据流
 * 2. 每 30 秒保存一条降采样记录到数据库
 * 
 * @property context Android 上下文
 * @property lifecycleOwner 生命周期拥有者
 * @property repository 光照数据仓库
 */
class LightMonitor(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val repository: LightRepository
) {
    companion object {
        private const val TAG = "LightMonitor"
        
        /** 保存间隔：30 秒 */
        private const val SAVE_INTERVAL_MS = 30 * 1000L
    }
    
    private var camera: LightSensorCamera? = null
    private var saveJob: Job? = null
    private var isRunning = false
    
    /** 最新的光照帧（用于保存） */
    private var latestFrame: LightFrame? = null
    
    /**
     * 启动光照监测
     * 
     * @param scope 协程作用域
     * @return 是否启动成功
     */
    fun start(scope: CoroutineScope): Boolean {
        if (isRunning) {
            LogX.w(TAG, "光照监测已在运行")
            return false
        }
        
        try {
            // 创建摄像头传感器
            camera = LightSensorCamera(context, lifecycleOwner)
            
            // 启动摄像头
            val cameraStarted = camera?.start(scope) ?: false
            if (!cameraStarted) {
                LogX.e(TAG, "摄像头启动失败")
                return false
            }
            
            // 订阅光照数据流
            scope.launch {
                camera?.lightFrameFlow?.collect { frame ->
                    latestFrame = frame
                }
            }
            
            // 启动定时保存任务
            saveJob = scope.launch {
                while (isRunning) {
                    delay(SAVE_INTERVAL_MS)
                    saveLatestFrame()
                }
            }
            
            isRunning = true
            LogX.i(TAG, "光照监测已启动")
            return true
            
        } catch (e: Exception) {
            LogX.e(TAG, "启动光照监测失败", e)
            stop()
            return false
        }
    }
    
    /**
     * 停止光照监测
     */
    fun stop() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        try {
            saveJob?.cancel()
            camera?.stop()
        } finally {
            saveJob = null
            camera = null
            latestFrame = null
            isRunning = false
        }
        LogX.i(TAG, "光照监测已停止")
    }
    
    /**
     * 保存最新的光照帧到数据库
     */
    private suspend fun saveLatestFrame() {
        val frame = latestFrame ?: return
        
        try {
            // 转换为领域模型
            val sample = LightSample(
                timestamp = frame.timestamp,
                avgLuma = frame.avgLuma,
                estimatedLux = frame.estimatedLux,
                level = frame.level
            )
            
            // 保存到数据库
            repository.insertSample(sample)
            
            LogX.d(TAG, "已保存光照采样 - 亮度: ${frame.relativeBrightness.toInt()}%, 等级: ${frame.level}")
            
        } catch (e: Exception) {
            LogX.e(TAG, "保存光照采样失败", e)
        }
    }
    
    /**
     * 获取实时光照数据流
     */
    val lightFrameFlow: Flow<LightFrame>?
        get() = camera?.lightFrameFlow
}
