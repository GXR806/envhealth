package com.GXR806.envhealth.data.sensor.audio

import com.GXR806.envhealth.data.repository.NoiseRepository
import com.GXR806.envhealth.domain.analysis.NoiseAnalysisResult
import com.GXR806.envhealth.domain.analysis.NoiseAnalyzer
import com.GXR806.envhealth.domain.model.NoiseSample
import com.GXR806.envhealth.util.LogX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 噪音监测管理器
 *
 * 整合 AudioNoiseRecorder 和 NoiseAnalyzer，提供统一的监测接口
 * 负责：
 * - 录音器启动/停止
 * - 噪音分析
 * - 数据落库（每 30 秒）
 * - 暴露实时分析结果
 */
class NoiseMonitor(
    private val repository: NoiseRepository,
    calibrationOffset: Float
) {
    companion object {
        private const val TAG = "NoiseMonitor"
        
        // 落库间隔：30 秒
        private const val SAVE_INTERVAL_MS = 30 * 1000L
    }

    private val recorder = AudioNoiseRecorder(calibrationOffset)
    private val analyzer = NoiseAnalyzer()

    private var analysisJob: Job? = null
    private var saveJob: Job? = null

    private val _analysisResultFlow = MutableStateFlow<NoiseAnalysisResult?>(null)
    val analysisResultFlow: StateFlow<NoiseAnalysisResult?> = _analysisResultFlow.asStateFlow()

    private var lastSavedResult: NoiseAnalysisResult? = null
    private var burstCountInInterval = 0  // 当前30秒内的突发次数
    private var lastBurstTimestamp = 0L  // 上次突发噪音的时间戳（用于2秒去重）

    /**
     * 开始监测
     *
     * @param scope 协程作用域
     * @return 是否成功启动
     */
    fun start(scope: CoroutineScope): Boolean {
        // 启动录音器
        val started = recorder.start(scope)
        if (!started) {
            LogX.e(TAG, "启动录音器失败")
            return false
        }

        // 启动分析协程
        analysisJob = scope.launch {
            recorder.noiseFrameFlow.collect { frame ->
                if (frame != null) {
                    // 分析噪音帧
                    val result = analyzer.analyze(frame)
                    
                    // 更新流
                    _analysisResultFlow.value = result
                    
                    // 保存最新结果（用于定期落库）
                    lastSavedResult = result
                    
                    // 累积突发噪音次数（2秒内去重）
                    if (result.isBurst) {
                        val now = result.timestamp
                        if (now - lastBurstTimestamp >= 2000) {
                            burstCountInInterval++
                            lastBurstTimestamp = now
                            LogX.d(TAG, "突发噪音累积: 当前30秒内第 $burstCountInInterval 次（已去重）")
                        } else {
                            LogX.d(TAG, "突发噪音去重: 距上次突发 ${now - lastBurstTimestamp} ms < 2000ms，忽略")
                        }
                    }
                }
            }
        }

        // 启动定期保存协程
        saveJob = scope.launch {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                
                // 保存到数据库（使用累积的实际突发次数）
                val result = lastSavedResult
                if (result != null) {
                    saveToDatabase(result, burstCountInInterval)
                    
                    // 重置突发计数器
                    if (burstCountInInterval > 0) {
                        LogX.i(TAG, "保存噪音数据: 30秒内检测到 $burstCountInInterval 次突发噪音")
                        burstCountInInterval = 0
                    }
                }
            }
        }

        LogX.i(TAG, "噪音监测已启动")
        return true
    }

    /**
     * 停止监测
     */
    suspend fun stop() {
        // 保存最后一次累积的数据（避免丢失未满30秒的数据）
        val result = lastSavedResult
        if (result != null && burstCountInInterval > 0) {
            LogX.i(TAG, "停止监测前保存最后的数据: 累积 $burstCountInInterval 次突发噪音")
            saveToDatabase(result, burstCountInInterval)
        }
        
        // 停止录音器
        recorder.stop()

        // 取消分析协程
        analysisJob?.cancel()
        analysisJob = null

        // 取消保存协程
        saveJob?.cancel()
        saveJob = null

        // 重置分析器
        analyzer.reset()

        // 清空流
        _analysisResultFlow.value = null
        lastSavedResult = null
        burstCountInInterval = 0
        lastBurstTimestamp = 0L

        LogX.i(TAG, "噪音监测已停止")
    }

    /**
     * 保存到数据库
     * 
     * @param result 噪音分析结果
     * @param actualBurstCount 这个时间间隔内实际检测到的突发次数
     */
    private suspend fun saveToDatabase(result: NoiseAnalysisResult, actualBurstCount: Int) {
        try {
            val sample = NoiseSample(
                timestamp = result.timestamp,
                leqDb = result.leq1s,
                maxDb = result.l10,
                peakDb = result.currentDb,
                isEvent = actualBurstCount > 0,  // 是否有突发
                burstCount = actualBurstCount  // 实际突发次数
            )

            repository.insertSample(sample)
            // LogX.d(TAG, "已保存噪音采样: ${result.currentDb} dB")  // 日志已禁用：过于频繁
        } catch (e: Exception) {
            LogX.e(TAG, "保存噪音采样失败", e)
        }
    }

    /**
     * 检查是否正在监测
     */
    fun isMonitoring(): Boolean = recorder.isRecording()
}
