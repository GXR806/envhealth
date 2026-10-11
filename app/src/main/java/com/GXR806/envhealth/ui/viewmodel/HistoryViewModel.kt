package com.GXR806.envhealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.GXR806.envhealth.di.AppContainer
import com.GXR806.envhealth.data.repository.HistoryData
import com.GXR806.envhealth.domain.analysis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZoneId

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Ready(val data: HistoryData) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

/** 维护开始立即撤销订阅与详情，generation 变化不得复活旧读取。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val period = MutableStateFlow(HistoryPeriod.DAY)
    private val offset = MutableStateFlow(0)
    private val refresh = MutableStateFlow(0)
    private val clock = flow {
        while (true) { emit(System.currentTimeMillis()); delay(60_000) }
    }
    private val selection = combine(period, offset) { p, offset -> p to offset }
    val state: StateFlow<HistoryUiState> = combine(selection, refresh, clock,
        container.maintenanceGate.generation, container.maintenanceGate.blocked
    ) { selection, retry, now, generation, blocked ->
        val (p, offset) = selection
        val zone = ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(now).atZone(zone)
        val anchor = when (p) {
            HistoryPeriod.DAY -> date.plusDays(offset.toLong())
            HistoryPeriod.WEEK -> date.plusWeeks(offset.toLong())
            HistoryPeriod.MONTH -> date.plusMonths(offset.toLong())
        }.toInstant().toEpochMilli()
        Triple(if (blocked) null else HistoryAggregator.range(anchor, p, zone), retry, generation)
    }
        .distinctUntilChanged()
        .flatMapLatest { (range, _, _) ->
            if (range == null) flowOf<HistoryUiState>(HistoryUiState.Error("数据维护中，请前往设置完成维护"))
            else container.historyRepository.observe(range).map<HistoryData, HistoryUiState> { HistoryUiState.Ready(it) }
                .onStart { emit(HistoryUiState.Loading) }
                .catch { e -> if (e is CancellationException) throw e else emit(HistoryUiState.Error("历史数据读取失败，请重试")) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HistoryUiState.Loading)
    fun move(delta: Int) {
        val minimum = when (period.value) { HistoryPeriod.DAY -> -90; HistoryPeriod.WEEK -> -13; HistoryPeriod.MONTH -> -3 }
        offset.value = (offset.value + delta).coerceIn(minimum, 0)
    }
    fun selectPeriod(value: HistoryPeriod) { offset.value = 0; period.value = value }
    fun today() { offset.value = 0; retry() }
    fun retry() { refresh.value++ }
}
