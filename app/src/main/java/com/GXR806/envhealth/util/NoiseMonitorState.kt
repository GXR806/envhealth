package com.GXR806.envhealth.util

import com.GXR806.envhealth.domain.analysis.NoiseAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 噪音监测全局状态
 *
 * 暴露实时噪音分析结果，供 UI 订阅显示
 */
object NoiseMonitorState {
    private val _analysisResult = MutableStateFlow<NoiseAnalysisResult?>(null)
    val analysisResult: StateFlow<NoiseAnalysisResult?> = _analysisResult.asStateFlow()

    /**
     * 更新分析结果
     */
    fun updateAnalysisResult(result: NoiseAnalysisResult?) {
        _analysisResult.value = result
    }

    /**
     * 清空状态
     */
    fun clear() {
        _analysisResult.value = null
    }
}
