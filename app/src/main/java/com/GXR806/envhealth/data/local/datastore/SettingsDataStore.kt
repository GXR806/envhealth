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
class SettingsDataStore(private val context: Context, private val injectedStore: DataStore<Preferences>? = null) {
    
    val store: DataStore<Preferences> get() = injectedStore ?: context.dataStore
    val snapshot: Flow<SettingsSnapshot> = store.data.map { p ->
        SettingsSnapshot(
            noiseThresholdDb = p[KEY_NOISE_THRESHOLD_DB] ?: DEFAULT_NOISE_THRESHOLD_DB,
            volumeThresholdPercent = p[KEY_VOLUME_THRESHOLD_PERCENT] ?: DEFAULT_VOLUME_THRESHOLD_PERCENT,
            continuousListeningThresholdMinutes = p[KEY_CONTINUOUS_LISTENING_THRESHOLD_MINUTES] ?: DEFAULT_CONTINUOUS_LISTENING_THRESHOLD_MINUTES,
            sleepStartHour = p[KEY_SLEEP_START_HOUR] ?: DEFAULT_SLEEP_START_HOUR,
            sleepStartMinute = p[KEY_SLEEP_START_MINUTE] ?: DEFAULT_SLEEP_START_MINUTE,
            sleepEndHour = p[KEY_SLEEP_END_HOUR] ?: DEFAULT_SLEEP_END_HOUR,
            sleepEndMinute = p[KEY_SLEEP_END_MINUTE] ?: DEFAULT_SLEEP_END_MINUTE,
            dbCalibrationOffset = p[KEY_DB_CALIBRATION_OFFSET] ?: DEFAULT_DB_CALIBRATION_OFFSET,
            backgroundMonitoringEnabled = p[KEY_BACKGROUND_MONITORING_ENABLED] ?: false,
            baselineVersion = p[KEY_BASELINE_VERSION] ?: 0L,
            maintenanceState = p[KEY_MAINTENANCE_STATE] ?: "",
            lastCleanupTimestamp = p[KEY_LAST_CLEANUP_TIMESTAMP] ?: 0L,
            themeMode = p[KEY_THEME_MODE]?.takeIf { it in setOf(
                SettingsSnapshot.THEME_MODE_SYSTEM, SettingsSnapshot.THEME_MODE_LIGHT, SettingsSnapshot.THEME_MODE_DARK
            ) } ?: SettingsSnapshot.THEME_MODE_SYSTEM
        )
    }

    suspend fun setThemeMode(mode: String) {
        require(mode in setOf(
            SettingsSnapshot.THEME_MODE_SYSTEM,
            SettingsSnapshot.THEME_MODE_LIGHT,
            SettingsSnapshot.THEME_MODE_DARK
        ))
        store.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setQuietTimes(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        require(startHour in 0..23 && endHour in 0..23 && startMinute in 0..59 && endMinute in 0..59)
        store.edit {
            it[KEY_SLEEP_START_HOUR] = startHour; it[KEY_SLEEP_START_MINUTE] = startMinute
            it[KEY_SLEEP_END_HOUR] = endHour; it[KEY_SLEEP_END_MINUTE] = endMinute
        }
    }

    suspend fun setMaintenanceState(state: String) {
        require(state in setOf("", "clearing", "error"))
        store.edit { it[KEY_MAINTENANCE_STATE] = state }
    }

    /** Room 清除成功且所有 writer 已停止后调用；设置/反馈恢复默认，后台明确关闭。 */
    suspend fun resetAll() {
        store.edit { it.clear(); it[KEY_BACKGROUND_MONITORING_ENABLED] = false }
    }

    companion object {
        private val KEY_THEME_MODE = androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")
        private val KEY_BASELINE_VERSION = longPreferencesKey("reminder_baseline_version")
        private val KEY_MAINTENANCE_STATE = androidx.datastore.preferences.core.stringPreferencesKey("maintenance_state")
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
    val noiseThresholdDb: Flow<Float> = store.data
        .map { preferences -> preferences[KEY_NOISE_THRESHOLD_DB] ?: DEFAULT_NOISE_THRESHOLD_DB }
    
    suspend fun setNoiseThresholdDb(value: Float) {
        require(value.isFinite() && value in 40f..90f)
        store.edit { preferences ->
            preferences[KEY_NOISE_THRESHOLD_DB] = value
            preferences[KEY_BASELINE_VERSION] = (preferences[KEY_BASELINE_VERSION] ?: 0L) + 1L
        }
    }
    
    // 噪音监测开关
    val noiseMonitoringEnabled: Flow<Boolean> = store.data
        .map { preferences -> preferences[KEY_NOISE_MONITORING_ENABLED] ?: false }
    
    suspend fun setNoiseMonitoringEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[KEY_NOISE_MONITORING_ENABLED] = enabled
        }
    }
    
    // 音量阈值百分比（0-100）
    val volumeThresholdPercent: Flow<Float> = store.data
        .map { preferences -> preferences[KEY_VOLUME_THRESHOLD_PERCENT] ?: DEFAULT_VOLUME_THRESHOLD_PERCENT }
    
    suspend fun setVolumeThresholdPercent(value: Float) {
        require(value.isFinite() && value in 20f..80f)
        store.edit { preferences ->
            preferences[KEY_VOLUME_THRESHOLD_PERCENT] = value
            preferences[KEY_BASELINE_VERSION] = (preferences[KEY_BASELINE_VERSION] ?: 0L) + 1L
        }
    }
    
    // 连续收听时长阈值（分钟）
    val continuousListeningThresholdMinutes: Flow<Int> = store.data
        .map { preferences -> preferences[KEY_CONTINUOUS_LISTENING_THRESHOLD_MINUTES] ?: DEFAULT_CONTINUOUS_LISTENING_THRESHOLD_MINUTES }
    
    suspend fun setContinuousListeningThresholdMinutes(value: Int) {
        require(value in 15..90)
        store.edit { preferences ->
            preferences[KEY_CONTINUOUS_LISTENING_THRESHOLD_MINUTES] = value
            preferences[KEY_BASELINE_VERSION] = (preferences[KEY_BASELINE_VERSION] ?: 0L) + 1L
        }
    }
    
    // 音量监测开关
    val volumeMonitoringEnabled: Flow<Boolean> = store.data
        .map { preferences -> preferences[KEY_VOLUME_MONITORING_ENABLED] ?: false }
    
    suspend fun setVolumeMonitoringEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[KEY_VOLUME_MONITORING_ENABLED] = enabled
        }
    }
    
    // 睡眠开始时间（小时）
    val sleepStartHour: Flow<Int> = store.data
        .map { preferences -> preferences[KEY_SLEEP_START_HOUR] ?: DEFAULT_SLEEP_START_HOUR }
    
    suspend fun setSleepStartHour(value: Int) {
        store.edit { preferences ->
            preferences[KEY_SLEEP_START_HOUR] = value
        }
    }
    
    // 睡眠开始时间（分钟）
    val sleepStartMinute: Flow<Int> = store.data
        .map { preferences -> preferences[KEY_SLEEP_START_MINUTE] ?: DEFAULT_SLEEP_START_MINUTE }
    
    suspend fun setSleepStartMinute(value: Int) {
        store.edit { preferences ->
            preferences[KEY_SLEEP_START_MINUTE] = value
        }
    }
    
    // 睡眠结束时间（小时）
    val sleepEndHour: Flow<Int> = store.data
        .map { preferences -> preferences[KEY_SLEEP_END_HOUR] ?: DEFAULT_SLEEP_END_HOUR }
    
    suspend fun setSleepEndHour(value: Int) {
        store.edit { preferences ->
            preferences[KEY_SLEEP_END_HOUR] = value
        }
    }
    
    // 睡眠结束时间（分钟）
    val sleepEndMinute: Flow<Int> = store.data
        .map { preferences -> preferences[KEY_SLEEP_END_MINUTE] ?: DEFAULT_SLEEP_END_MINUTE }
    
    suspend fun setSleepEndMinute(value: Int) {
        store.edit { preferences ->
            preferences[KEY_SLEEP_END_MINUTE] = value
        }
    }
    
    // 睡眠监测开关
    val sleepMonitoringEnabled: Flow<Boolean> = store.data
        .map { preferences -> preferences[KEY_SLEEP_MONITORING_ENABLED] ?: false }
    
    suspend fun setSleepMonitoringEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[KEY_SLEEP_MONITORING_ENABLED] = enabled
        }
    }
    
    // 分贝校准偏移
    val dbCalibrationOffset: Flow<Float> = store.data
        .map { preferences -> preferences[KEY_DB_CALIBRATION_OFFSET] ?: DEFAULT_DB_CALIBRATION_OFFSET }
    
    suspend fun setDbCalibrationOffset(value: Float) {
        require(value.isFinite() && value in 0f..120f)
        store.edit { preferences ->
            preferences[KEY_DB_CALIBRATION_OFFSET] = value
        }
    }
    
    // Lux 估算系数
    val luxEstimationFactor: Flow<Float> = store.data
        .map { preferences -> preferences[KEY_LUX_ESTIMATION_FACTOR] ?: DEFAULT_LUX_ESTIMATION_FACTOR }
    
    suspend fun setLuxEstimationFactor(value: Float) {
        store.edit { preferences ->
            preferences[KEY_LUX_ESTIMATION_FACTOR] = value
        }
    }
    
    // 后台监测总开关
    val backgroundMonitoringEnabled: Flow<Boolean> = store.data
        .map { preferences -> preferences[KEY_BACKGROUND_MONITORING_ENABLED] ?: false }
    
    suspend fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[KEY_BACKGROUND_MONITORING_ENABLED] = enabled
        }
    }
    
    // 上次清理时间戳
    val lastCleanupTimestamp: Flow<Long> = store.data
        .map { preferences -> preferences[KEY_LAST_CLEANUP_TIMESTAMP] ?: 0L }
    
    suspend fun setLastCleanupTimestamp(value: Long) {
        store.edit { preferences ->
            preferences[KEY_LAST_CLEANUP_TIMESTAMP] = value
        }
    }
}
