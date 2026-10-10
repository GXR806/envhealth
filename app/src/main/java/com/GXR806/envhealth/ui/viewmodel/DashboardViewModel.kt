package com.GXR806.envhealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.GXR806.envhealth.di.AppContainer
import com.GXR806.envhealth.domain.model.SleepReport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface DashboardReportState {
    data object Loading : DashboardReportState
    data class Ready(val report: SleepReport?) : DashboardReportState
    data object Error : DashboardReportState
}
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    val noise = com.GXR806.envhealth.util.NoiseMonitorState.analysisResult
    val light = com.GXR806.envhealth.util.LightMonitorState.lightFrame
    val headphone = container.headphoneGuard.state
    private val _headphoneLoading = MutableStateFlow(true)
    val headphoneLoading = _headphoneLoading.asStateFlow()
    private val refresh = MutableStateFlow(0)
    val report = combine(container.maintenanceGate.generation, container.maintenanceGate.blocked, refresh) { g, b, r -> Triple(g, b, r) }
        .flatMapLatest { (_, blocked, _) ->
            if (blocked) flowOf<DashboardReportState>(DashboardReportState.Ready(null))
            else container.sleepRepository.getRecentReports(1)
                .map<List<SleepReport>, DashboardReportState> { DashboardReportState.Ready(it.firstOrNull()) }
                .onStart { emit(DashboardReportState.Loading) }
                .catch { e -> if (e is CancellationException) throw e else emit(DashboardReportState.Error) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), DashboardReportState.Loading)
    fun refreshHeadphones() { viewModelScope.launch {
        _headphoneLoading.value = true
        try { container.headphoneGuard.refreshHistory() }
        finally { _headphoneLoading.value = false }
    } }
    fun retry() { refresh.value++; refreshHeadphones() }
}
