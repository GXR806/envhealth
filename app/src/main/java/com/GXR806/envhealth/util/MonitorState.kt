package com.GXR806.envhealth.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局监测状态管理
 *
 * 单例对象，管理应用的监测状态，暴露 Flow 供 UI 层订阅
 */
object MonitorState {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun reportError(message: String) { _error.value = message }
    fun clearError() { _error.value = null }
    private val _isMonitoring = MutableStateFlow(false)

    /**
     * 是否正在监测（Flow）
     */
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    /**
     * 设置监测状态
     */
    fun setMonitoring(monitoring: Boolean) {
        _isMonitoring.value = monitoring
        LogX.i("MonitorState", "监测状态变更: $monitoring")
    }

    /**
     * 获取当前监测状态（非响应式）
     */
    fun isMonitoringNow(): Boolean = _isMonitoring.value
}
