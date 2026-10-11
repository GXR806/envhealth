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
    calibrationOffset: Float,
    private val batch: com.GXR806.envhealth.data.sensor.BoundedSampleBatch<NoiseSample>
) {
    companion object {
        private const val TAG = "NoiseMonitor"
        
        // 落库间隔：30 秒
        private const val SAVE_INTERVAL_MS = 30 * 1000L
    }

    private val recorder = AudioNoiseRecorder(calibrationOffset)
    private val analyzer = NoiseAnalyzer()

    /** 只改变后续帧；Leq 滑动窗口至多 1 分钟含旧校准帧，不修改算法或历史记录。 */
    fun updateCalibrationOffset(value: Float) = recorder.updateCalibrationOffset(value)

    private var lastQueuedTimestamp: Long? = null
    private suspend fun writeError() {
        com.GXR806.envhealth.util.MonitorState.reportError("噪音保存失败，已暂停采集；最多3条特征留在内存，请停止重试。进程终止可能丢失。")
        LogX.e(TAG, "噪音批次写入失败，暂停采集")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            recorder.stop()
            recorder.awaitStopped()
        }
        analysisJob?.cancel()
    }
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
        analysisJob = scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
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
        saveJob = scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            while (true) {
                delay(SAVE_INTERVAL_MS)
                
                try {
                    val result = lastSavedResult
                    if (result != null && result.timestamp != lastQueuedTimestamp) {
                        saveToDatabase(result, burstCountInInterval)
                    }
                    batch.flushIfDue()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { writeError(); break }
            }
        }

        LogX.i(TAG, "噪音监测已启动")
        return true
    }

    /**
     * 停止监测
     */
    suspend fun stop() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 先释放麦克风，最终数据库写入失败也不能保留采集资源。
        try {
            recorder.stop()
            recorder.awaitStopped()
        } finally {
            analysisJob?.cancel()
            analysisJob?.join()
            analysisJob = null
            saveJob?.cancel()
            saveJob?.join()
            saveJob = null
        }
        try {
            val result = lastSavedResult
            if (!batch.failed && result != null && result.timestamp != lastQueuedTimestamp) {
                saveToDatabase(result, burstCountInInterval)
            }
            batch.flush()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { writeError() } finally {
            analyzer.reset()
            _analysisResultFlow.value = null
            lastSavedResult = null
            burstCountInInterval = 0
            lastBurstTimestamp = 0L
        }
        LogX.i(TAG, "噪音监测已停止")
    }

    /**
     * 保存到数据库
     * 
     * @param result 噪音分析结果
     * @param actualBurstCount 这个时间间隔内实际检测到的突发次数
     */
    private suspend fun saveToDatabase(result: NoiseAnalysisResult, actualBurstCount: Int) {
        val sample = NoiseSample(timestamp = result.timestamp, leqDb = result.leq1s,
            maxDb = result.l10, peakDb = result.currentDb,
            isEvent = actualBurstCount > 0, burstCount = actualBurstCount)
        // 已接收到有界批次即更新游标；事务失败时该条仍保留，停止重试不能重复加入。
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            lastQueuedTimestamp = result.timestamp
            burstCountInInterval = 0
            batch.offer(sample)
        }
    }

    /**
     * 检查是否正在监测
     */
    fun isMonitoring(): Boolean = recorder.isRecording()
}
