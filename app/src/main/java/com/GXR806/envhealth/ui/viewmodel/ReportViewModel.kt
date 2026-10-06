package com.GXR806.envhealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.GXR806.envhealth.data.repository.AdviceRepository
import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.analysis.SleepAnalyzer
import com.GXR806.envhealth.domain.model.AdviceItem
import com.GXR806.envhealth.domain.model.SleepReport
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.domain.sleep.SleepSessionManager
import com.GXR806.envhealth.domain.usecase.GenerateHealthAdviceUseCase
import com.GXR806.envhealth.domain.usecase.GenerateSleepReportUseCase
import com.GXR806.envhealth.util.LogX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 报告页面 ViewModel
 * 
 * 职责：
 * 1. 管理睡眠会话（开始/结束/取消）
 * 2. 生成睡眠报告和健康建议
 * 3. 提供 UI 状态管理
 */
class ReportViewModel(
    private val context: android.content.Context,
    private val sleepSessionManager: SleepSessionManager,
    private val generateSleepReportUseCase: GenerateSleepReportUseCase,
    private val generateHealthAdviceUseCase: GenerateHealthAdviceUseCase,
    private val sleepRepository: SleepRepository,
    private val adviceRepository: AdviceRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "ReportViewModel"
    }
    
    // ========== UI State ==========
    
    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    
    private val _currentSession = MutableStateFlow<SleepSession?>(null)
    val currentSession: StateFlow<SleepSession?> = _currentSession.asStateFlow()
    
    private val _latestReport = MutableStateFlow<SleepReport?>(null)
    val latestReport: StateFlow<SleepReport?> = _latestReport.asStateFlow()
    
    private val _latestAdvice = MutableStateFlow<List<AdviceItem>>(emptyList())
    val latestAdvice: StateFlow<List<AdviceItem>> = _latestAdvice.asStateFlow()
    
    init {
        loadCurrentSession()
        loadLatestReportAndAdvice()
    }
    
    // ========== Public API ==========
    
    /**
     * 开始睡眠会话
     */
    fun startSleepSession() {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            
            // 1. 启动环境监测服务
            val monitorStarted = com.GXR806.envhealth.service.EnvMonitorService.startMonitoring(context)
            if (!monitorStarted) {
                LogX.w(TAG, "启动环境监测服务失败，可能是后台启动限制")
                // 仍然继续创建会话，但可能不会采集数据
            }
            
            // 2. 开始睡眠会话
            sleepSessionManager.startManualSession()
                .onSuccess { sessionId ->
                    LogX.i(TAG, "睡眠会话已开始: sessionId=$sessionId")
                    loadCurrentSession()
                    _uiState.value = ReportUiState.SessionStarted(sessionId)
                }
                .onFailure { error ->
                    LogX.e(TAG, "开始睡眠会话失败", error)
                    _uiState.value = ReportUiState.Error(error.message ?: "开始失败")
                }
        }
    }
    
    /**
     * 结束睡眠会话并生成报告
     */
    fun endSleepSession() {
        viewModelScope.launch {
            val session = _currentSession.value
            if (session == null) {
                _uiState.value = ReportUiState.Error("当前没有进行中的会话")
                return@launch
            }
            
            _uiState.value = ReportUiState.Loading
            
            // 1. 结束会话
            sleepSessionManager.endSession(session.id)
                .onSuccess {
                    LogX.i(TAG, "睡眠会话已结束: sessionId=${session.id}")
                    
                    // 2. 重新加载会话（获取更新后的状态）
                    val updatedSession = sleepRepository.getSessionById(session.id)
                    if (updatedSession == null) {
                        _uiState.value = ReportUiState.Error("会话未找到")
                        return@onSuccess
                    }
                    
                    // 3. 生成报告
                    generateSleepReportUseCase(updatedSession)
                        .onSuccess { (report, analysisResult) ->
                            _latestReport.value = report
                            LogX.i(TAG, "睡眠报告已生成: reportId=${report.id}")
                            
                            // 4. 生成建议
                            generateHealthAdviceUseCase(analysisResult)
                                .onSuccess { advice ->
                                    _latestAdvice.value = advice
                                    LogX.i(TAG, "健康建议已生成: ${advice.size} 条")
                                    
                                    _currentSession.value = null
                                    _uiState.value = ReportUiState.ReportGenerated(report, advice)
                                }
                                .onFailure { error ->
                                    LogX.e(TAG, "生成健康建议失败", error)
                                    // 即使建议生成失败，报告仍然有效
                                    _currentSession.value = null
                                    _uiState.value = ReportUiState.ReportGenerated(report, emptyList())
                                }
                        }
                        .onFailure { error ->
                            LogX.e(TAG, "生成报告失败", error)
                            _uiState.value = ReportUiState.Error(error.message ?: "生成报告失败")
                        }
                }
                .onFailure { error ->
                    LogX.e(TAG, "结束睡眠会话失败", error)
                    _uiState.value = ReportUiState.Error(error.message ?: "结束失败")
                }
        }
    }
    
    /**
     * 取消睡眠会话
     */
    fun cancelSleepSession() {
        viewModelScope.launch {
            val session = _currentSession.value
            if (session == null) {
                _uiState.value = ReportUiState.Error("当前没有进行中的会话")
                return@launch
            }
            
            _uiState.value = ReportUiState.Loading
            
            sleepSessionManager.cancelSession(session.id)
                .onSuccess {
                    LogX.i(TAG, "睡眠会话已取消: sessionId=${session.id}")
                    _currentSession.value = null
                    _uiState.value = ReportUiState.SessionCancelled
                }
                .onFailure { error ->
                    LogX.e(TAG, "取消睡眠会话失败", error)
                    _uiState.value = ReportUiState.Error(error.message ?: "取消失败")
                }
        }
    }
    
    /**
     * 重置 UI 状态
     */
    fun resetUiState() {
        _uiState.value = ReportUiState.Idle
    }
    
    // ========== Private Methods ==========
    
    private fun loadCurrentSession() {
        viewModelScope.launch {
            val session = sleepSessionManager.getOngoingSessionOnce()
            _currentSession.value = session
        }
    }
    
    private fun loadLatestReportAndAdvice() {
        viewModelScope.launch {
            // 加载最近的报告
            val recentSessions = sleepRepository.getRecentCompletedSessions(1).first()
            if (recentSessions.isNotEmpty()) {
                val latestSession = recentSessions.first()
                val report = sleepRepository.getReportBySessionId(latestSession.id)
                _latestReport.value = report
                
                // 不要自动加载历史建议，只显示当前会话生成的建议
                // 历史建议会在生成报告时通过 ReportGenerated 状态传递
            }
        }
    }
}

/**
 * 报告页面 UI 状态
 */
sealed class ReportUiState {
    object Idle : ReportUiState()
    object Loading : ReportUiState()
    data class SessionStarted(val sessionId: Long) : ReportUiState()
    object SessionCancelled : ReportUiState()
    data class ReportGenerated(val report: SleepReport, val advice: List<AdviceItem>) : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}
