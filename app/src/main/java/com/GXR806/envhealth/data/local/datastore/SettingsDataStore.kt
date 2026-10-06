package com.GXR806.envhealth.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用设置数据存储
 * 使用 DataStore Preferences 保存用户配置
 */
class SettingsDataStore(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        
        // 噪音监测设置
        val KEY_NOISE_THRESHOLD_DB = floatPreferencesKey("noise_threshold_db")
        val KEY_NOISE_MONITORING_ENABLED = booleanPreferencesKey("noise_monitoring_enabled")
        
        // 音量监测设置
        val KEY_VOLUME_THRESHOLD_PERCENT = floatPreferencesKey("volume_threshold_percent")
        val KEY_CONTINUOUS_LISTENING_THRESHOLD_MINUTES = intPreferencesKey("continuous_listening_threshold_minutes")
        val KEY_VOLUME_MONITORING_ENABLED = booleanPreferencesKey("volume_monitoring_enabled")
        
        // 睡眠监测设置
        val KEY_SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
        val KEY_SLEEP_START_MINUTE = intPreferencesKey("sleep_start_minute")
        val KEY_SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
        val KEY_SLEEP_END_MINUTE = intPreferencesKey("sleep_end_minute")
        val KEY_SLEEP_MONITORING_ENABLED = booleanPreferencesKey("sleep_monitoring_enabled")
        
        // 校准参数
        val KEY_DB_CALIBRATION_OFFSET = floatPreferencesKey("db_calibration_offset")
        val KEY_LUX_ESTIMATION_FACTOR = floatPreferencesKey("lux_estimation_factor")
        
        // 后台监测总开关
        val KEY_BACKGROUND_MONITORING_ENABLED = booleanPreferencesKey("background_monitoring_enabled")
        
        // 数据清理设置
        val KEY_LAST_CLEANUP_TIMESTAMP = longPreferencesKey("last_cleanup_timestamp")
        
        // 默认值
        const val DEFAULT_NOISE_THRESHOLD_DB = 70f
        const val DEFAULT_VOLUME_THRESHOLD_PERCENT = 60f
        const val DEFAULT_CONTINUOUS_LISTENING_THRESHOLD_MINUTES = 60
        const val DEFAULT_SLEEP_START_HOUR = 22
        const val DEFAULT_SLEEP_START_MINUTE = 0
        const val DEFAULT_SLEEP_END_HOUR = 7
        const val DEFAULT_SLEEP_END_MINUTE = 0
        
        /** 默认校准偏移：96.0 dB（dBFS -> dB SPL）
         * 
         * 说明：
         * - dBFS 范围：-96 dB 到 0 dB（数字信号满量程）
         * - dB SPL 范围：约 30-90 dB（声压级，真实环境噪音）
         * - 校准偏移 = 96 dB，使得：
         *   - 安静环境（-66 dBFS）→ 30 dB SPL
         *   - 正常说话（-36 dBFS）→ 60 dB SPL
         *   - 嘈杂环境（-16 dBFS）→ 80 dB SPL
         * 
         * 注意：此值为估算值，实际需要通过标准声级计校准
         */
        const val DEFAULT_DB_CALIBRATION_OFFSET = 96f
        const val DEFAULT_LUX_ESTIMATION_FACTOR = 1f
    }
    
    // 噪音阈值（dB，估算值）
    val noiseThresholdDb: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[KEY_NOISE_THRESHOLD_DB] ?: DEFAULT_NOISE_THRESHOLD_DB }
    
    suspend fun setNoiseThresholdDb(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOISE_THRESHOLD_DB] = value
        }
    }
    
    // 噪音监测开关
    val noiseMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_NOISE_MONITORING_ENABLED] ?: false }
    
    suspend fun setNoiseMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOISE_MONITORING_ENABLED] = enabled
        }
    }
    
    // 音量阈值百分比（0-100）
    val volumeThresholdPercent: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[KEY_VOLUME_THRESHOLD_PERCENT] ?: DEFAULT_VOLUME_THRESHOLD_PERCENT }
    
    suspend fun setVolumeThresholdPercent(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VOLUME_THRESHOLD_PERCENT] = value
        }
    }
    
    // 连续收听时长阈值（分钟）
    val continuousListeningThresholdMinutes: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_CONTINUOUS_LISTENING_THRESHOLD_MINUTES] ?: DEFAULT_CONTINUOUS_LISTENING_THRESHOLD_MINUTES }
    
    suspend fun setContinuousListeningThresholdMinutes(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CONTINUOUS_LISTENING_THRESHOLD_MINUTES] = value
        }
    }
    
    // 音量监测开关
    val volumeMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_VOLUME_MONITORING_ENABLED] ?: false }
    
    suspend fun setVolumeMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VOLUME_MONITORING_ENABLED] = enabled
        }
    }
    
    // 睡眠开始时间（小时）
    val sleepStartHour: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_SLEEP_START_HOUR] ?: DEFAULT_SLEEP_START_HOUR }
    
    suspend fun setSleepStartHour(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SLEEP_START_HOUR] = value
        }
    }
    
    // 睡眠开始时间（分钟）
    val sleepStartMinute: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_SLEEP_START_MINUTE] ?: DEFAULT_SLEEP_START_MINUTE }
    
    suspend fun setSleepStartMinute(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SLEEP_START_MINUTE] = value
        }
    }
    
    // 睡眠结束时间（小时）
    val sleepEndHour: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_SLEEP_END_HOUR] ?: DEFAULT_SLEEP_END_HOUR }
    
    suspend fun setSleepEndHour(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SLEEP_END_HOUR] = value
        }
    }
    
    // 睡眠结束时间（分钟）
    val sleepEndMinute: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_SLEEP_END_MINUTE] ?: DEFAULT_SLEEP_END_MINUTE }
    
    suspend fun setSleepEndMinute(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SLEEP_END_MINUTE] = value
        }
    }
    
    // 睡眠监测开关
    val sleepMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_SLEEP_MONITORING_ENABLED] ?: false }
    
    suspend fun setSleepMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SLEEP_MONITORING_ENABLED] = enabled
        }
    }
    
    // 分贝校准偏移
    val dbCalibrationOffset: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[KEY_DB_CALIBRATION_OFFSET] ?: DEFAULT_DB_CALIBRATION_OFFSET }
    
    suspend fun setDbCalibrationOffset(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DB_CALIBRATION_OFFSET] = value
        }
    }
    
    // Lux 估算系数
    val luxEstimationFactor: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[KEY_LUX_ESTIMATION_FACTOR] ?: DEFAULT_LUX_ESTIMATION_FACTOR }
    
    suspend fun setLuxEstimationFactor(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LUX_ESTIMATION_FACTOR] = value
        }
    }
    
    // 后台监测总开关
    val backgroundMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_BACKGROUND_MONITORING_ENABLED] ?: false }
    
    suspend fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKGROUND_MONITORING_ENABLED] = enabled
        }
    }
    
    // 上次清理时间戳
    val lastCleanupTimestamp: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[KEY_LAST_CLEANUP_TIMESTAMP] ?: 0L }
    
    suspend fun setLastCleanupTimestamp(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_CLEANUP_TIMESTAMP] = value
        }
    }
}
