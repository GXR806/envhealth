package com.GXR806.envhealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.GXR806.envhealth.data.sensor.volume.HeadphoneGuard

/** 守护生命周期归前台服务所有，页面离开不停止、不自动启动守护。 */
class HeadphoneGuardViewModel(private val guard: HeadphoneGuard) : ViewModel() {
    val uiState = guard.state
    init {
        // 只读取历史汇总，不启动服务或系统监听。
        retry()
    }
    fun retry() { viewModelScope.launch { guard.refreshHistory() } }
}
