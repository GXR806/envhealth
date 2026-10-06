package com.GXR806.envhealth.data.sensor.light

import android.content.Context
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.GXR806.envhealth.domain.model.LightLevel
import com.GXR806.envhealth.util.Constants
import com.GXR806.envhealth.util.LogX
import com.GXR806.envhealth.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * 光线传感器（基于摄像头）
 * 
 * 使用 CameraX 估算环境光照强度
 * 
 * ## 实现原理（降级模式）
 * 1. **YUV 亮度采样**：从 YUV_420_888 的 Y 平面计算平均亮度（0-255）
 * 2. **相对亮度指数**：将 avgLuma 映射到 0-100 范围
 * 3. **光照等级判断**：基于相对亮度分级（黑暗/昏暗/适中/明亮）
 * 
 * ## 降级模式说明
 * 当前版本未获取曝光参数（exposureTime、ISO），因此无法精确计算 lux 值。
 * 使用相对亮度指数（0-100）代替，仍可满足基本的光照等级判断需求。
 * 
 * ## 输出频率
 * 1 秒 1 帧（避免频繁计算）
 * 
 * @property context Android 上下文
 * @property lifecycleOwner 生命周期拥有者（用于绑定 CameraX）
 */
class LightSensorCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "LightSensorCamera"
        
        /** 采样间隔：1 秒 */
        private const val SAMPLE_INTERVAL_MS = 1000L
        
        /** 分辨率：640x480 */
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
    }
    
    private val _lightFrameFlow = MutableSharedFlow<LightFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /** 光照数据流 */
    val lightFrameFlow: Flow<LightFrame> = _lightFrameFlow.asSharedFlow()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var isRunning = false
    private var samplingJob: Job? = null
    
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
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    setupCamera(scope)
                    isRunning = true
                    LogX.i(TAG, "光照监测已启动")
                } catch (e: Exception) {
                    LogX.e(TAG, "初始化 CameraProvider 失败", e)
                }
            }, ContextCompat.getMainExecutor(context))
            
            return true
        } catch (e: Exception) {
            LogX.e(TAG, "启动光照监测失败", e)
            return false
        }
    }
    
    /**
     * 停止光照监测
     */
    fun stop() {
        if (!isRunning) {
            return
        }
        
        try {
            samplingJob?.cancel()
            samplingJob = null
            
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageAnalysis = null
            
            isRunning = false
            LogX.i(TAG, "光照监测已停止")
        } catch (e: Exception) {
            LogX.e(TAG, "停止光照监测失败", e)
        }
    }
    
    /**
     * 设置摄像头
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun setupCamera(scope: CoroutineScope) {
        val provider = cameraProvider ?: return
        
        try {
            // 前置摄像头选择器
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            // ImageAnalysis 配置
            val analysisBuilder = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(TARGET_WIDTH, TARGET_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            
            // Camera2 扩展配置
            val camera2Interop = Camera2Interop.Extender(analysisBuilder)
            camera2Interop.setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON
            )
            
            imageAnalysis = analysisBuilder.build().apply {
                setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    analyzeImage(imageProxy)
                }
            }
            
            // 绑定到生命周期
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
            
            // 启动定时采样（1 秒 1 帧）
            startSampling(scope)
            
            LogX.i(TAG, "摄像头已绑定 - 分辨率: ${TARGET_WIDTH}x${TARGET_HEIGHT}")
        } catch (e: Exception) {
            LogX.e(TAG, "设置摄像头失败", e)
        }
    }
    
    /**
     * 启动定时采样
     */
    private fun startSampling(scope: CoroutineScope) {
        samplingJob = scope.launch {
            while (isRunning) {
                // 1 秒采样一次（避免频繁计算）
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * 分析图像帧
     */
    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            // 获取 YUV Y 平面
            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            
            // 计算平均亮度
            val avgLuma = calculateAvgLuma(yBuffer, ySize)
            
            // 降级模式：不获取曝光参数，使用相对亮度指数
            val relativeBrightness = (avgLuma / 255f) * 100f
            
            // 判断光照等级（基于相对亮度）
            val level = LightLevel.fromRelativeBrightness(relativeBrightness)
            
            // 发射光照帧
            val frame = LightFrame(
                timestamp = TimeUtils.nowMillis(),
                avgLuma = avgLuma,
                estimatedLux = null, // 降级模式：无法精确估算 lux
                relativeBrightness = relativeBrightness,
                level = level,
                exposureTimeNs = null,
                iso = null
            )
            
            _lightFrameFlow.tryEmit(frame)
            
        } catch (e: Exception) {
            LogX.e(TAG, "分析图像失败", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * 计算平均亮度（YUV Y 平面）
     */
    private fun calculateAvgLuma(yBuffer: java.nio.ByteBuffer, ySize: Int): Float {
        var sum = 0L
        val originalPosition = yBuffer.position()
        
        for (i in 0 until ySize) {
            // ByteBuffer 存储的是有符号字节，需要转换为无符号（0-255）
            val y = yBuffer.get(originalPosition + i).toInt() and 0xFF
            sum += y
        }
        
        return (sum / ySize.toFloat()).coerceIn(0f, 255f)
    }
}
