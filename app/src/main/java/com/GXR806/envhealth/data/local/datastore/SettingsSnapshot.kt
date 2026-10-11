package com.GXR806.envhealth.data.local.datastore

/** 原子快照避免 quiet time 四个独立 Flow 出现中间组合。 */
data class SettingsSnapshot(
    val noiseThresholdDb: Float = 70f,
    val volumeThresholdPercent: Float = 60f,
    val continuousListeningThresholdMinutes: Int = 60,
    val sleepStartHour: Int = 22,
    val sleepStartMinute: Int = 0,
    val sleepEndHour: Int = 7,
    val sleepEndMinute: Int = 0,
    val dbCalibrationOffset: Float = 96f,
    val backgroundMonitoringEnabled: Boolean = false,
    val baselineVersion: Long = 0,
    val maintenanceState: String = "",
    val lastCleanupTimestamp: Long = 0,
    /** 主题模式：SYSTEM、LIGHT 或 DARK。 */
    val privacyConsentVersion: Int = 0,
    val themeMode: String = THEME_MODE_SYSTEM
) {
    val hasPrivacyConsent: Boolean get() = privacyConsentVersion == PRIVACY_POLICY_VERSION
    companion object {
        const val PRIVACY_POLICY_VERSION = 1
        const val THEME_MODE_SYSTEM = "SYSTEM"
        const val THEME_MODE_LIGHT = "LIGHT"
        const val THEME_MODE_DARK = "DARK"
    }
}

