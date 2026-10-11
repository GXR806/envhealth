package com.GXR806.envhealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.GXR806.envhealth.di.AppContainer
import com.GXR806.envhealth.data.local.datastore.SettingsSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val loadedSettings = container.settingsRepository.snapshot.map { it as SettingsSnapshot? }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings = container.settingsRepository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsSnapshot())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private fun operation(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block(); mutableMessage.value = "操作成功" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableMessage.value = "操作失败：${e.message}（清除失败可再次确认重试）" }
            finally { mutableBusy.value = false }
        }
    }
    fun acceptPrivacy() = operation {
        container.maintenanceGate.write { container.settingsRepository.acceptPrivacyPolicy() }
        container.restoreConsentedWork()
    }
    fun theme(mode: String) = operation { container.maintenanceGate.write { container.settingsRepository.setThemeMode(mode) } }
    fun background(enabled: Boolean) = operation { container.maintenance.setBackground(enabled) }
    fun noise(value: Float) = operation { container.maintenanceGate.write { container.settingsRepository.setNoiseThresholdDb(value) } }
    fun volume(value: Float) = operation { container.maintenanceGate.write { container.settingsRepository.setVolumeThresholdPercent(value) } }
    fun duration(value: Int) = operation { container.maintenanceGate.write { container.settingsRepository.setContinuousListeningThresholdMinutes(value) } }
    fun calibrationText(text: String) = operation {
        val value = text.trim().toFloatOrNull()
        require(value != null && value.isFinite() && value in 0f..120f) { "校准偏移请输入 0–120 范围内的有限数字，可含小数" }
        container.maintenanceGate.write { container.settingsRepository.setDbCalibrationOffset(value) }
    }
    fun quiet(start: String, end: String) = operation {
        fun parse(text: String): Pair<Int, Int> {
            require(Regex("\\d{1,2}:\\d{2}").matches(text)) { "时间格式应为 HH:mm" }
            val parts = text.split(':').map { it.toInt() }
            require(parts[0] in 0..23 && parts[1] in 0..59) { "时间超出范围" }
            return parts[0] to parts[1]
        }
        val (sh, sm) = parse(start); val (eh, em) = parse(end)
        container.maintenanceGate.write { container.settingsRepository.setQuietTimes(sh, sm, eh, em) }
    }
    val reminderDiagnostics = container.reminderDiagnostics.asStateFlow()
    private val mutableReminderHistory = MutableStateFlow("尚未读取持久提醒记录")
    val reminderHistory = mutableReminderHistory.asStateFlow()
    fun refreshReminderHistory() {
        val generation = container.maintenanceGate.generation.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.maintenanceGate.write(generation) {
                    val stored = container.settingsRepository.reminderStore.read()
                    mutableReminderHistory.value = com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type.entries.joinToString("\n") { type ->
                        val event = stored.events.filter { it.type == type }.maxByOrNull { it.sentAt }
                        fun time(at: Long?) = at?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString() } ?: "无"
                        "${type.name}：最近已保存 notify 事件=${time(event?.sentAt)}${if (event?.silent == true) "（静默）" else ""}；节流占位=${time(stored.lastSent[type])}；7天静音截止=${time(stored.quietUntil[type])}"
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableReminderHistory.value = "持久提醒记录读取失败或正在维护；未重置节流" }
        }
    }
    fun checkHealthReminder() {
        mutableMessage.value = if (com.GXR806.envhealth.service.EnvMonitorService.requestReminderCheck())
            "已请求运行中的服务检查真实策略；不会绕过24小时/7天冷却或静默，不是测试通知。"
        else "监测未运行，未发送；请回首页明确手动开始。"
        refreshReminderHistory()
    }
    fun clearAll() = operation {
        container.maintenance.clearAll()
        mutableReminderHistory.value = "提醒记录已清除"
    }
}
