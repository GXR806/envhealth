package com.GXR806.envhealth.data.repository

import androidx.room.Embedded
import com.GXR806.envhealth.data.local.room.entity.SleepReportEntity

/** 查询投影，不是实体，不改变 schema。 */
data class HistoryReportRow(
    @Embedded val report: SleepReportEntity,
    val sessionStartTime: Long
)
