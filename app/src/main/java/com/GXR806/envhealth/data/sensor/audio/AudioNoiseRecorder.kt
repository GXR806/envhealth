package com.GXR806.envhealth.data.sensor.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.GXR806.envhealth.util.Constants
import com.GXR806.envhealth.util.LogX
import com.GXR806.envhealth.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 音频噪音录音器
 *
 * 使用 AudioRecord 实时采集环境噪音，计算声学指标：
 * - RMS → dBFS
 * - dBFS → dB SPL（考虑校准偏移）
 * - A 计权滤波
 * - 每 100ms 输出一帧数据
 */
class AudioNoiseRecorder(
    calibrationOffset: Float = Constants.Audio.DEFAULT_CALIBRATION_OFFSET
) {
    @Volatile private var calibrationOffset: Float = calibrationOffset

    /** 最新校准在下一帧生效；一帧只读取一次，历史数据不重算。 */
    fun updateCalibrationOffset(value: Float) {
        require(value.isFinite() && value in 0f..120f)
        calibrationOffset = value
    }
    companion object {
        private const val TAG = "AudioNoiseRecorder"
        
        // AudioRecord 配置
        private const val SAMPLE_RATE = Constants.Audio.SAMPLE_RATE
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2 // 缓冲区倍数
        
        // 采样参数
        private const val FRAME_DURATION_MS = Constants.Audio.FRAME_DURATION_MS
        private const val SAMPLES_PER_FRAME = Constants.Audio.SAMPLES_PER_FRAME
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val aWeightingFilter = AWeightingFilter(SAMPLE_RATE)

    private val _noiseFrameFlow = MutableStateFlow<NoiseFrame?>(null)
    val noiseFrameFlow: StateFlow<NoiseFrame?> = _noiseFrameFlow.asStateFlow()

    private var isRecording = false

    /**
     * 开始录音
     *
     * @param scope 协程作用域
     * @return 是否成功启动
     */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        if (isRecording) {
            LogX.w(TAG, "录音已在运行中")
            return false
        }

        try {
            // 计算缓冲区大小
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                LogX.e(TAG, "无法获取最小缓冲区大小: $minBufferSize")
                return false
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

            // 创建 AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                LogX.e(TAG, "AudioRecord 初始化失败")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            // 启动录音
            audioRecord?.startRecording()
            isRecording = true

            // 重置 A 计权滤波器状态
            aWeightingFilter.reset()

            // 启动录音协程
            recordingJob = scope.launch(Dispatchers.IO) {
                recordLoop()
            }

            LogX.i(TAG, "录音已启动 - 采样率: $SAMPLE_RATE Hz, 缓冲区: $bufferSize bytes")
            return true

        } catch (e: Exception) {
            LogX.e(TAG, "启动录音失败", e)
            stop()
            return false
        }
    }

    /**
     * 停止录音
     */
    private var stoppedRecordingJob: Job? = null
    suspend fun awaitStopped() {
        stoppedRecordingJob?.join()
        stoppedRecordingJob = null
        _noiseFrameFlow.value = null
    }
    fun stop() {
        isRecording = false
        stoppedRecordingJob = recordingJob
        recordingJob?.cancel()
        recordingJob = null
        val record = audioRecord
        audioRecord = null
        try {
            record?.stop()
        } catch (e: Exception) {
            LogX.e(TAG, "停止录音时出错", e)
        } finally {
            try {
                record?.release()
            } catch (e: Exception) {
                LogX.e(TAG, "释放录音器时出错", e)
            } finally {
                _noiseFrameFlow.value = null
            }
        }
    }

    /**
     * 录音循环
     */
    private suspend fun recordLoop() {
        val buffer = ShortArray(SAMPLES_PER_FRAME)

        while (isRecording) {
            try {
                // 读取一帧数据
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readCount > 0) {
                    // 处理这一帧数据
                    processFrame(buffer, readCount)
                } else if (readCount < 0) {
                    LogX.e(TAG, "读取音频数据失败: $readCount")
                }

                // 等待到下一帧（100ms）
                delay(FRAME_DURATION_MS.toLong())

            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (isRecording) {
                    LogX.e(TAG, "录音循环出错", e)
                }
                break
            }
        }

        LogX.i(TAG, "录音循环已退出")
    }

    /**
     * 处理一帧数据
     */
    private fun processFrame(samples: ShortArray, count: Int) {
        if (count <= 0) return

        val timestamp = TimeUtils.nowMillis()
        val offset = calibrationOffset // 一帧一致快照

        // 1. 计算原始 RMS
        val rms = calculateRMS(samples, count)

        // 2. RMS → dBFS
        val dbFs = rmsToDbFS(rms)

        // 3. dBFS → dB SPL（考虑校准偏移）
        val dbSpl = dbFs + offset

        // 4. A 计权处理
        val rmsA = aWeightingFilter.processAndCalculateRMS(samples.copyOf(count))
        val dbFsA = rmsToDbFS(rmsA.toFloat())
        val dbA = dbFsA + offset

        // 5. 输出帧数据
        val frame = NoiseFrame(
            timestamp = timestamp,
            dbFs = dbFs,
            dbSpl = dbSpl,
            dbA = dbA,
            rms = rms
        )

        _noiseFrameFlow.value = frame
    }

    /**
     * 计算 RMS（Root Mean Square）
     */
    private fun calculateRMS(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f

        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = samples[i].toDouble()
            sumSquares += sample * sample
        }

        return sqrt(sumSquares / count).toFloat()
    }

    /**
     * RMS → dBFS（Full Scale）
     *
     * dBFS = 20 * log10(rms / 32767)
     *
     * @param rms RMS 值
     * @return dBFS，范围：-∞ ~ 0
     */
    private fun rmsToDbFS(rms: Float): Float {
        if (rms <= 0f) return -96f // 静音时返回 -96 dBFS

        // 16-bit PCM 满量程为 32767
        val fullScale = 32767f
        return 20f * log10(rms / fullScale)
    }

    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording
}
