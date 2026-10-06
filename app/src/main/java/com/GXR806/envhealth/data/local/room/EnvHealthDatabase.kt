package com.GXR806.envhealth.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.GXR806.envhealth.data.local.room.dao.AdviceDao
import com.GXR806.envhealth.data.local.room.dao.HeadphoneUsageDao
import com.GXR806.envhealth.data.local.room.dao.LightSampleDao
import com.GXR806.envhealth.data.local.room.dao.NoiseSampleDao
import com.GXR806.envhealth.data.local.room.dao.SleepReportDao
import com.GXR806.envhealth.data.local.room.dao.SleepSessionDao
import com.GXR806.envhealth.data.local.room.entity.AdviceEntity
import com.GXR806.envhealth.data.local.room.entity.HeadphoneUsageEntity
import com.GXR806.envhealth.data.local.room.entity.LightSampleEntity
import com.GXR806.envhealth.data.local.room.entity.NoiseSampleEntity
import com.GXR806.envhealth.data.local.room.entity.SleepReportEntity
import com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity

/**
 * 环境健康数据库
 * 
 * 数据保留策略：
 * - 原始采样（noise_samples, light_samples）：保留 7 天
 * - 聚合报告与记录（sleep_sessions, sleep_reports, headphone_usage, advice_items）：保留 90 天
 */
@Database(
    entities = [
        NoiseSampleEntity::class,
        LightSampleEntity::class,
        SleepSessionEntity::class,
        SleepReportEntity::class,
        HeadphoneUsageEntity::class,
        AdviceEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class EnvHealthDatabase : RoomDatabase() {
    
    abstract fun noiseSampleDao(): NoiseSampleDao
    abstract fun lightSampleDao(): LightSampleDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepReportDao(): SleepReportDao
    abstract fun headphoneUsageDao(): HeadphoneUsageDao
    abstract fun adviceDao(): AdviceDao
    
    companion object {
        const val DATABASE_NAME = "env_health.db"
    }
}
