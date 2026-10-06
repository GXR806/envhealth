package com.GXR806.envhealth.data.sensor.light

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 光线传感器（基于摄像头）
 * 
 * 使用 CameraX 估算环境光照强度（lux）
 * 
 * ## 实现原理
 * 1. **YUV 亮度采样**：从 YUV_420_888 的 Y 平面计算平均亮度（0-255）
 * 2. **曝光参数获取**：通过 Camera2 Interop CaptureCallback 获取 SENSOR_EXPOSURE_TIME（ns）和 SENSOR_SENSITIVITY（ISO）
 * 3. **Lux 估算公式**（工程近似）：
 *    ```
 *    lux ≈ (avgLuma / 255) × K / (exposureTimeSec × (iso / 100))
 *    ```
 *    其中 K 为校准常数（默认 2500，基于实验估算）
 * 4. **光照等级判断**：基于 lux 值分级（黑暗 <5 / 昏暗 5-50 / 适中 50-300 / 明亮 >300）
 * 
 * ## 降级模式
 * 若无法获取曝光参数，退化为相对亮度指数（0-100），仍可提供基本的光照等级判断。
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
    @Volatile private var stopped = false
    private var analysisExecutor: java.util.concurrent.ExecutorService? = null
    private var stoppedExecutor: java.util.concurrent.ExecutorService? = null
    private var stoppedSamplingJob: Job? = null
    suspend fun awaitStopped() {
        stoppedSamplingJob?.join()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val executor = stoppedExecutor
            if (executor != null) {
                while (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                }
            }
        }
        stoppedExecutor = null
        stoppedSamplingJob = null
    }
    
    // 原子变量存储最新的曝光参数（通过 CaptureCallback 更新）
    private val latestExposureTimeNs = AtomicLong(0L)
    private val latestIso = AtomicReference<Int?>(null)
    
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
        
        stopped = false
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                if (stopped) return@addListener
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
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        // 即使 Provider 尚未返回也禁止延迟回调再次绑定相机。
        stopped = true
        isRunning = false
        stoppedSamplingJob = samplingJob
        samplingJob?.cancel()
        samplingJob = null
        try {
            imageAnalysis?.clearAnalyzer()
        } finally {
            try {
                cameraProvider?.unbindAll() // CameraX @MainThread
            } finally {
                stoppedExecutor = analysisExecutor
                analysisExecutor?.shutdown()
                analysisExecutor = null
                cameraProvider = null
                imageAnalysis = null
                latestExposureTimeNs.set(0L)
                latestIso.set(null)
            }
        }
    }
    
    /**
     * 设置摄像头
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun setupCamera(scope: CoroutineScope) {
        val provider = cameraProvider ?: return
        
        try {
            // 前置摄像头选择器（用于睡眠环境监测）
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            // ImageAnalysis 配置
            val analysisBuilder = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(TARGET_WIDTH, TARGET_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            
            // Camera2 扩展配置（启用自动曝光 + CaptureCallback 监听曝光参数）
            val camera2Interop = Camera2Interop.Extender(analysisBuilder)
            camera2Interop.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            
            // 设置 CaptureCallback 监听曝光参数
            camera2Interop.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    
                    // 获取曝光时间（ns）
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { exposureTime ->
                        latestExposureTimeNs.set(exposureTime)
                    }
                    
                    // 获取 ISO 感光度
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { iso ->
                        latestIso.set(iso)
                    }
                }
            })
            
            val executor = Executors.newSingleThreadExecutor()
            analysisExecutor = executor
            imageAnalysis = analysisBuilder.build().apply {
                setAnalyzer(executor) { imageProxy ->
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
            
            LogX.i(TAG, "摄像头已绑定 - 分辨率: ${TARGET_WIDTH}x${TARGET_HEIGHT}，使用后置摄像头")
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
            if (stopped) return
            // 获取 YUV Y 平面
            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            
            // 计算平均亮度
            val avgLuma = calculateAvgLuma(yBuffer, ySize)
            
            // 从原子变量读取最新的曝光参数
            val exposureTimeNs = latestExposureTimeNs.get()
            val iso = latestIso.get()
            
            // 计算 Lux 估算值
            var estimatedLux: Float? = null
            if (exposureTimeNs > 0 && iso != null && iso > 0) {
                estimatedLux = calculateLux(avgLuma, exposureTimeNs, iso)
            }
            
            // 计算相对亮度（0-100）
            val relativeBrightness = (avgLuma / 255f) * 100f
            
            // 判断光照等级
            val level = if (estimatedLux != null) {
                LightLevel.fromLux(estimatedLux)
            } else {
                LightLevel.fromRelativeBrightness(relativeBrightness)
            }
            
            // 发射光照帧
            val frame = LightFrame(
                timestamp = TimeUtils.nowMillis(),
                avgLuma = avgLuma,
                estimatedLux = estimatedLux,
                relativeBrightness = relativeBrightness,
                level = level,
                exposureTimeNs = if (exposureTimeNs > 0) exposureTimeNs else null,
                iso = iso
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
    
    /**
     * 计算 Lux 估算值（工程近似公式）
     * 
     * 公式：lux ≈ (avgLuma / 255) × K / (exposureTimeSec × (iso / 100))
     * 
     * 其中：
     * - avgLuma：平均亮度（0-255）
     * - K：校准常数（默认 2500）
     * - exposureTimeSec：曝光时间（秒）
     * - iso：ISO 感光度
     * 
     * 注意：此公式为工程近似，实际需要通过标准照度计校准
     * 
     * @param avgLuma 平均亮度（0-255）
     * @param exposureTimeNs 曝光时间（ns）
     * @param iso ISO 感光度
     * @return 估算的 lux 值
     */
    private fun calculateLux(avgLuma: Float, exposureTimeNs: Long, iso: Int): Float {
        // 将曝光时间从纳秒转换为秒
        val exposureTimeSec = exposureTimeNs / 1_000_000_000.0
        
        // 归一化亮度（0-1）
        val normalizedLuma = avgLuma / 255f
        
        // 归一化 ISO（以 100 为基准）
        val normalizedIso = iso / 100f
        
        // 应用 Lux 估算公式
        val lux = normalizedLuma * Constants.Light.DEFAULT_LUX_K / (exposureTimeSec.toFloat() * normalizedIso)
        
        // 限制在合理范围（0-100000 lux）
        return lux.coerceIn(0f, 100000f)
    }
}
