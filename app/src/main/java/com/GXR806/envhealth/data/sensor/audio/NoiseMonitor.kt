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
                }
            }
        }

        // 启动定期保存协程
        saveJob = scope.launch {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                
                // 保存到数据库
                val result = lastSavedResult
                if (result != null) {
                    saveToDatabase(result)
                }
            }
        }

        LogX.i(TAG, "噪音监测已启动")
        return true
    }

    /**
     * 停止监测
     */
    fun stop() {
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

        LogX.i(TAG, "噪音监测已停止")
    }

    /**
     * 保存到数据库
     */
    private suspend fun saveToDatabase(result: NoiseAnalysisResult) {
        try {
            val sample = NoiseSample(
                timestamp = result.timestamp,
                leqDb = result.leq1s,
                maxDb = result.l10,
                peakDb = result.currentDb,
                isEvent = result.isBurst
            )

            repository.insertSample(sample)
            LogX.d(TAG, "已保存噪音采样: ${result.currentDb} dB")
        } catch (e: Exception) {
            LogX.e(TAG, "保存噪音采样失败", e)
        }
    }

    /**
     * 检查是否正在监测
     */
    fun isMonitoring(): Boolean = recorder.isRecording()
}
