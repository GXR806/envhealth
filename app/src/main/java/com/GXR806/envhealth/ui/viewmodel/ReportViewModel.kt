package com.GXR806.envhealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.GXR806.envhealth.data.repository.AdviceRepository
import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.model.*
import com.GXR806.envhealth.domain.sleep.SleepSessionManager
import com.GXR806.envhealth.domain.usecase.GenerateHealthAdviceUseCase
import com.GXR806.envhealth.domain.usecase.GenerateSleepReportUseCase
import com.GXR806.envhealth.EnvHealthApplication
import com.GXR806.envhealth.service.EnvMonitorService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 整个会话→报告→建议链共享维护 gate；不在 gate 内等待 Service 停止。 */
class ReportViewModel(
    private val context: android.content.Context,
    private val sleepSessionManager: SleepSessionManager,
    private val generateSleepReportUseCase: GenerateSleepReportUseCase,
    private val generateHealthAdviceUseCase: GenerateHealthAdviceUseCase,
    private val sleepRepository: SleepRepository,
    private val adviceRepository: AdviceRepository
) : ViewModel() {
    private val container = (context.applicationContext as EnvHealthApplication).appContainer
    private val gate = container.maintenanceGate
    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _currentSession = MutableStateFlow<SleepSession?>(null)
    val currentSession = _currentSession.asStateFlow()
    private val _latestReport = MutableStateFlow<SleepReport?>(null)
    val latestReport = _latestReport.asStateFlow()
    private val _latestAdvice = MutableStateFlow<List<AdviceItem>>(emptyList())
    val latestAdvice = _latestAdvice.asStateFlow()
    init {
        viewModelScope.launch {
            gate.generation.collect { generation ->
                _currentSession.value = null
                _latestReport.value = null
                _latestAdvice.value = emptyList()
                _uiState.value = ReportUiState.Idle
                try {
                    gate.write(generation) {
                        _currentSession.value = sleepSessionManager.getOngoingSessionOnce()
                        val session = sleepRepository.getRecentCompletedSessions(1).first().firstOrNull()
                        _latestReport.value = session?.let { sleepRepository.getReportBySessionId(it.id) }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* 维护期保持空缓存 */ }
            }
        }
    }
    private fun action(block: suspend () -> Unit) {
        val generation = gate.generation.value
        viewModelScope.launch {
            try {
                gate.write(generation) {
                    check(container.settingsRepository.snapshot.first().maintenanceState.isEmpty()) { "数据维护未完成" }
                    _uiState.value = ReportUiState.Loading
                    block()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = ReportUiState.Error(e.message ?: "操作失败") }
        }
    }
    fun startSleepSession() = action {
        check(container.settingsRepository.snapshot.first().backgroundMonitoringEnabled) { "请在首页设置中开启后台监测，再手动开始" }
        check(EnvMonitorService.startMonitoring(context)) { "启动监测失败，请检查权限并从首页启动" }
        val id = sleepSessionManager.startManualSession().getOrThrow()
        _currentSession.value = sleepSessionManager.getOngoingSessionOnce()
        _uiState.value = ReportUiState.SessionStarted(id)
    }
    fun endSleepSession() = action {
        val session = checkNotNull(_currentSession.value) { "当前没有进行中的会话" }
        sleepSessionManager.endSession(session.id).getOrThrow()
        val updated = checkNotNull(sleepRepository.getSessionById(session.id)) { "会话未找到" }
        val (report, analysis) = generateSleepReportUseCase(updated).getOrThrow()
        val advice = generateHealthAdviceUseCase(analysis).getOrThrow()
        _latestReport.value = report
        _latestAdvice.value = advice
        _currentSession.value = null
        _uiState.value = ReportUiState.ReportGenerated(report, advice)
    }
    fun cancelSleepSession() = action {
        val session = checkNotNull(_currentSession.value) { "当前没有进行中的会话" }
        sleepSessionManager.cancelSession(session.id).getOrThrow()
        _currentSession.value = null
        _uiState.value = ReportUiState.SessionCancelled
    }
    fun resetUiState() { _uiState.value = ReportUiState.Idle }
}

sealed class ReportUiState {
    object Idle : ReportUiState()
    object Loading : ReportUiState()
    data class SessionStarted(val sessionId: Long) : ReportUiState()
    object SessionCancelled : ReportUiState()
    data class ReportGenerated(val report: SleepReport, val advice: List<AdviceItem>) : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}
