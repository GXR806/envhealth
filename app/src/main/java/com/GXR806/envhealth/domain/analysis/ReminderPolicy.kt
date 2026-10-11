package com.GXR806.envhealth.domain.analysis

/** 提醒策略与 P6 风险等级独立；所有时间使用毫秒墙钟，后退时保守不发送。 */
object ReminderPolicy {
    const val DAY_MS = 24 * 60 * 60_000L
    const val QUIET_MS = 7 * DAY_MS
    const val RESPONSE_WINDOW_MS = DAY_MS
    const val HISTORY_MS = 30 * DAY_MS
    const val FRESH_MS = 2 * 60_000L
    const val MIN_RESPONSES = 5

    enum class Type { NOISE, VOLUME, DURATION, WEEKLY }
    enum class Action { LOWER_VOLUME, LATER, MUTE }
    data class Event(
        val id: String,
        val type: Type,
        val sentAt: Long,
        val silent: Boolean,
        val baselineVersion: Long,
        val action: Action? = null,
        val actionAt: Long? = null,
        val effective: Boolean = false
    )
    data class Feedback(val eligible: Int, val responded: Int) {
        val rate: Double get() = if (eligible == 0) 0.0 else responded.toDouble() / eligible
    }
    data class Baselines(val noise: Float = 70f, val volume: Float = 60f, val duration: Int = 60)
    data class LiveRisk(
        val noiseDb: Float? = null,
        val noiseAt: Long? = null,
        val volume: Float? = null,
        val volumeAt: Long? = null,
        val connectedAndPlaying: Boolean = false,
        val durationMs: Long = 0,
        val weekMs: Long = 0
    )

    fun maySend(now: Long, lastSent: Long?, quietUntil: Long?): Boolean {
        if (now < 0 || (quietUntil != null && now < quietUntil)) return false
        return lastSent == null || (now >= lastSent && now - lastSent >= DAY_MS)
    }

    /** 起止相同定义为全天静默；否则左闭右开，支持跨午夜。 */
    fun isQuiet(minuteOfDay: Int, start: Int, end: Int): Boolean {
        require(minuteOfDay in 0..1439 && start in 0..1439 && end in 0..1439)
        return when {
            start == end -> true
            start < end -> minuteOfDay >= start && minuteOfDay < end
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }

    fun fresh(now: Long, at: Long?): Boolean = at != null && now >= at && now - at <= FRESH_MS

    /** 分母仅为已经发送且完整经过 24h 响应窗口的事件，不把新通知算作忽略。
     * 手动修改基线版本后仅用新版本事件适应，不让旧反馈覆盖手动意图。
     */
    fun feedback(events: List<Event>, type: Type, now: Long, baselineVersion: Long): Feedback {
        val eligible = events.filter {
            it.type == type && it.baselineVersion == baselineVersion &&
                now >= it.sentAt && now - it.sentAt in RESPONSE_WINDOW_MS..HISTORY_MS
        }
        return Feedback(eligible.size, eligible.count {
            it.action == Action.LOWER_VOLUME && it.effective && it.actionAt != null &&
                it.actionAt >= it.sentAt && it.actionAt - it.sentAt <= RESPONSE_WINDOW_MS
        })
    }

    fun adjustment(feedback: Feedback): Int = when {
        feedback.eligible < MIN_RESPONSES -> 0
        feedback.rate >= 0.5 -> -5
        feedback.rate < 0.2 -> 5
        else -> 0
    }

    fun personalized(base: Baselines, events: List<Event>, now: Long, version: Long): Baselines = Baselines(
        (base.noise + adjustment(feedback(events, Type.NOISE, now, version))).coerceIn(40f, 90f),
        (base.volume + adjustment(feedback(events, Type.VOLUME, now, version))).coerceIn(20f, 80f),
        (base.duration + adjustment(feedback(events, Type.DURATION, now, version))).coerceIn(15, 90)
    )

    /** 严重风险不受适应放宽影响；旧读数不触发当前噪音/音量/连续收听提醒。 */
    fun risks(live: LiveRisk, thresholds: Baselines, now: Long): Set<Type> = buildSet {
        if (fresh(now, live.noiseAt) && live.noiseDb?.let { it.isFinite() && it > thresholds.noise } == true) add(Type.NOISE)
        val volume = live.volume
        if (live.connectedAndPlaying && fresh(now, live.volumeAt) && volume != null && volume.isFinite()) {
            if (volume > thresholds.volume || volume > 80f) add(Type.VOLUME)
            if (live.durationMs > thresholds.duration * 60_000L) add(Type.DURATION)
        }
        if (live.weekMs > 40 * 60 * 60_000L) add(Type.WEEKLY)
    }

    /** 档位向下取整，严格不提高；已经为零时不执行，也不记有效响应。 */
    fun lowerVolumeTarget(current: Int, max: Int, threshold: Float): Int? {
        if (current <= 0 || max <= 0 || !threshold.isFinite()) return null
        val safe = kotlin.math.floor(max * threshold.coerceIn(0f, 60f) / 100.0).toInt()
        return minOf(current - 1, safe).coerceAtLeast(0)
    }

    fun validAction(event: Event, type: Type, now: Long): Boolean =
        event.type == type && event.action == null && now >= event.sentAt && now - event.sentAt <= RESPONSE_WINDOW_MS
}

/** 保留策略：保护未结束会话；完成耳机区间按 endTime 保守过期。
 * 等于截止线仍保留（严格 <），避免边界数据与进行中的会话被删除。
 */
object RetentionPolicy {
    const val SAMPLE_MS = 7 * ReminderPolicy.DAY_MS
    const val AGGREGATE_MS = 90 * ReminderPolicy.DAY_MS
    fun cleanupDue(now: Long, last: Long?): Boolean =
        last == null || (now >= last && now - last >= ReminderPolicy.DAY_MS)
    fun sampleExpired(timestamp: Long, now: Long): Boolean = timestamp < now - SAMPLE_MS
    fun completedExpired(endTime: Long?, now: Long): Boolean = endTime != null && endTime < now - AGGREGATE_MS
}
