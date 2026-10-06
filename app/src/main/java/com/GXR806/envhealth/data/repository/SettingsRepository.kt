package com.GXR806.envhealth.data.repository

import android.content.Context
import com.GXR806.envhealth.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow

/**
 * 应用设置数据仓库
 * 负责用户配置的持久化与查询
 */
class SettingsRepository(context: Context, store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null) {
    
    private val settingsDataStore = SettingsDataStore(context.applicationContext, store)
    val snapshot = settingsDataStore.snapshot
    val reminderStore = ReminderStore(settingsDataStore.store)
    suspend fun setQuietTimes(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
        settingsDataStore.setQuietTimes(startHour, startMinute, endHour, endMinute)
    suspend fun setMaintenanceState(state: String) = settingsDataStore.setMaintenanceState(state)
    suspend fun resetAll() = settingsDataStore.resetAll()
    
    // ========== 噪音监测设置 ==========
    
    val noiseThresholdDb: Flow<Float> = settingsDataStore.noiseThresholdDb
    
    suspend fun setNoiseThresholdDb(value: Float) {
        settingsDataStore.setNoiseThresholdDb(value)
    }
    
    val noiseMonitoringEnabled: Flow<Boolean> = settingsDataStore.noiseMonitoringEnabled
    
    suspend fun setNoiseMonitoringEnabled(enabled: Boolean) {
        settingsDataStore.setNoiseMonitoringEnabled(enabled)
    }
    
    // ========== 音量监测设置 ==========
    
    val volumeThresholdPercent: Flow<Float> = settingsDataStore.volumeThresholdPercent
    
    suspend fun setVolumeThresholdPercent(value: Float) {
        settingsDataStore.setVolumeThresholdPercent(value)
    }
    
    val continuousListeningThresholdMinutes: Flow<Int> = settingsDataStore.continuousListeningThresholdMinutes
    
    suspend fun setContinuousListeningThresholdMinutes(value: Int) {
        settingsDataStore.setContinuousListeningThresholdMinutes(value)
    }
    
    val volumeMonitoringEnabled: Flow<Boolean> = settingsDataStore.volumeMonitoringEnabled
    
    suspend fun setVolumeMonitoringEnabled(enabled: Boolean) {
        settingsDataStore.setVolumeMonitoringEnabled(enabled)
    }
    
    // ========== 睡眠监测设置 ==========
    
    val sleepStartHour: Flow<Int> = settingsDataStore.sleepStartHour
    
    suspend fun setSleepStartHour(value: Int) {
        settingsDataStore.setSleepStartHour(value)
    }
    
    val sleepStartMinute: Flow<Int> = settingsDataStore.sleepStartMinute
    
    suspend fun setSleepStartMinute(value: Int) {
        settingsDataStore.setSleepStartMinute(value)
    }
    
    val sleepEndHour: Flow<Int> = settingsDataStore.sleepEndHour
    
    suspend fun setSleepEndHour(value: Int) {
        settingsDataStore.setSleepEndHour(value)
    }
    
    val sleepEndMinute: Flow<Int> = settingsDataStore.sleepEndMinute
    
    suspend fun setSleepEndMinute(value: Int) {
        settingsDataStore.setSleepEndMinute(value)
    }
    
    val sleepMonitoringEnabled: Flow<Boolean> = settingsDataStore.sleepMonitoringEnabled
    
    suspend fun setSleepMonitoringEnabled(enabled: Boolean) {
        settingsDataStore.setSleepMonitoringEnabled(enabled)
    }
    
    // ========== 校准参数 ==========
    
    val dbCalibrationOffset: Flow<Float> = settingsDataStore.dbCalibrationOffset
    
    suspend fun setDbCalibrationOffset(value: Float) {
        settingsDataStore.setDbCalibrationOffset(value)
    }
    
    val luxEstimationFactor: Flow<Float> = settingsDataStore.luxEstimationFactor
    
    suspend fun setLuxEstimationFactor(value: Float) {
        settingsDataStore.setLuxEstimationFactor(value)
    }
    
    // ========== 后台监测总开关 ==========
    
    val backgroundMonitoringEnabled: Flow<Boolean> = settingsDataStore.backgroundMonitoringEnabled
    
    suspend fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        settingsDataStore.setBackgroundMonitoringEnabled(enabled)
    }
    
    // ========== 数据清理设置 ==========
    
    val lastCleanupTimestamp: Flow<Long> = settingsDataStore.lastCleanupTimestamp
    
    suspend fun setLastCleanupTimestamp(value: Long) {
        settingsDataStore.setLastCleanupTimestamp(value)
    }
}
