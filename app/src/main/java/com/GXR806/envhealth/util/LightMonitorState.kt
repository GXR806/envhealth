package com.GXR806.envhealth.util

import com.GXR806.envhealth.data.sensor.light.LightFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 光照监测全局状态
 * 
 * 用于在 Service 和 UI 之间共享光照监测数据
 * Service 负责更新数据，UI 负责订阅显示
 */
object LightMonitorState {
    
    private val _lightFrame: MutableStateFlow<LightFrame?> = MutableStateFlow(null)
    
    /**
     * 当前光照监测帧数据
     * UI 层订阅此 Flow 来实时显示光照数据
     */
    val lightFrame: StateFlow<LightFrame?> = _lightFrame.asStateFlow()
    
    /**
     * 更新光照监测数据
     * 由 Service 调用
     */
    fun updateLightFrame(frame: LightFrame?) {
        _lightFrame.value = frame
    }
    
    /**
     * 清空光照监测数据
     * 在停止监测时调用
     */
    fun clear() {
        _lightFrame.value = null
    }
}
