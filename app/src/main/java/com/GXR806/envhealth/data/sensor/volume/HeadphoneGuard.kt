package com.GXR806.envhealth.data.sensor.volume

import android.content.Context
import android.os.SystemClock
import com.GXR806.envhealth.data.repository.HeadphoneRepository
import com.GXR806.envhealth.domain.analysis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** 服务与 ViewModel 共享的状态。写入只发生在单一事件消费者中。 */
data class HeadphoneGuardState(
    val running: Boolean = false,
    val reading: VolumeReading? = null,
    val durationMs: Long = 0L,
    val avgPercent: Float = 0f,
    val maxPercent: Float = 0f,
    val todayMs: Long = 0L,
    val weekMs: Long = 0L,
    val risk: RiskResult = RiskEvaluator().evaluate(0, 0f, 0),
    val error: String? = null
)

class HeadphoneGuard(context: Context, private val repository: HeadphoneRepository) {
    private val monitor = VolumeMonitor(context.applicationContext)
    private var tracker = HeadphoneUsageTracker()
    /** 仅在服务完整停止后调用，丢弃失败待写项，防止清除后复活。 */
    @Volatile private var cacheGeneration = 0L
    @Volatile private var active = false
    // 仅同步短状态发布/撤销，不持锁执行任何数据库或挂起操作。
    private val publicationLock = Any()
    private var completed = emptyList<com.GXR806.envhealth.domain.model.HeadphoneUsage>()
    private var historyKnown = false
    private var lastHistoryPoll = Long.MIN_VALUE
    private var historyDate: LocalDate? = null
    fun clearStoppedCache() {
        check(events == null && stopSignal == null)
        cacheGeneration++
        tracker = HeadphoneUsageTracker()
        completed = emptyList()
        historyKnown = false
        lastHistoryPoll = Long.MIN_VALUE
        historyDate = null
        active = false
        mutableState.value = HeadphoneGuardState()
    }
    private val mutableState = MutableStateFlow(HeadphoneGuardState())
    val state = mutableState.asStateFlow()
    private data class Event(val reading: VolumeReading? = null, val error: String? = null,
                             val stop: CompletableDeferred<Unit>? = null,
                             val stopMono: Long = 0L)
    private var events: Channel<Event>? = null
    private var consumer: Job? = null
    private var scope: CoroutineScope? = null
    private var stopSignal: CompletableDeferred<Unit>? = null

    /** 页面冷启动只读历史；返回时若服务已启动，不覆盖实时状态。 */
    suspend fun refreshHistory() {
        val generation = cacheGeneration
        if (events != null) return
        try {
            val records = withContext(Dispatchers.IO) { repository.getCompletedIntervals() }
            if (events != null || generation != cacheGeneration) return
            completed = records
            historyKnown = true
            lastHistoryPoll = SystemClock.elapsedRealtime()
            historyDate = LocalDate.now()
            val now = System.currentTimeMillis()
            val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val unsaved = tracker.pending.toList()
            val today = clippedDuration(records + unsaved, midnight, now)
            val week = clippedDuration(records + unsaved, now - 7 * 24 * 60 * 60_000L, now)
            mutableState.value = mutableState.value.copy(todayMs = today, weekMs = week,
                risk = RiskEvaluator().evaluate(0, 0f, week))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (events == null && generation == cacheGeneration) mutableState.value = mutableState.value.copy(error = "读取耳机历史失败：${e.message}")
        }
    }

    /** 生命周期入口由服务主线程调用，重复启动不会建立第二条会话。 */
    fun start() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        if (events != null || stopSignal != null) return
        val channel = Channel<Event>(Channel.UNLIMITED)
        events = channel
        active = true
        val generation = cacheGeneration
        // 每次明确启动重新读取；失败时保留上次已知历史而非伪造周累计为零。
        lastHistoryPoll = Long.MIN_VALUE
        val exceptions = CoroutineExceptionHandler { _, failure ->
            if (failure is Error) {
                Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), failure)
                throw failure
            }
            mutableState.value = mutableState.value.copy(running = false, error = "耳机任务异常，已请求释放监听")
            com.GXR806.envhealth.util.LogX.e("HeadphoneGuard", "耳机任务异常")
            // invokeOnCompletion仍负责Main注销/完成stop信号；不在handler中等待。
        }
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptions)
        scope = owner
        mutableState.value = mutableState.value.copy(running = true, error = null)
        consumer = owner.launch {
            for (event in channel) {
                var error = event.error ?: mutableState.value.error
                val reading = event.reading ?: mutableState.value.reading
                if (event.stop != null) tracker.stop(event.stopMono)
                else event.reading?.let {
                    tracker.update(it.device, it.playing, it.percent, it.mono, it.wall)
                }
                fun publish() {
                    if (generation != cacheGeneration) return
                    val now = reading?.wall ?: System.currentTimeMillis()
                    val midnight = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
                        .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val records = completed + tracker.pending + listOfNotNull(tracker.liveSegment())
                    val today = if (historyKnown) clippedDuration(records, midnight, now) else mutableState.value.todayMs
                    val week = if (historyKnown) clippedDuration(records, now - 7 * 24 * 60 * 60_000L, now) else mutableState.value.weekMs
                    synchronized(publicationLock) {
                    if (generation != cacheGeneration) return@synchronized
                    val current = active && event.stop == null
                    mutableState.value = HeadphoneGuardState(
                        running = current, reading = if (current) reading else null,
                        durationMs = tracker.durationMs, avgPercent = tracker.avgVolumePercent,
                        maxPercent = tracker.maxVolumePercent, todayMs = today, weekMs = week,
                        risk = RiskEvaluator().evaluate(if (current) tracker.durationMs else 0,
                            if (current && reading?.device != null && reading.playing) reading.percent else 0f, week),
                        error = error ?: if (!historyKnown) "耳机历史尚未加载，周累计暂不可确认" else null
                    )
                    }
                }
                // 实时音量风险先发布，不等待保存或历史查询；单一消费者保留所有权重事件。
                publish()
                try {
                    while (tracker.pending.isNotEmpty()) {
                        val pending = tracker.pending.first()
                        val id = repository.insertUsage(pending)
                        completed = completed + pending.copy(id = id)
                        tracker.pending.removeAt(0)
                    }
                    val mono = SystemClock.elapsedRealtime()
                    val date = LocalDate.now()
                    if (active && event.stop == null && (lastHistoryPoll == Long.MIN_VALUE || mono - lastHistoryPoll >= 60_000L || date != historyDate)) {
                        // 失败也节流，避免每个音量事件扫描90天；成功替换包含刚写入区间的缓存。
                        lastHistoryPoll = mono
                        historyDate = date
                        val records = repository.getCompletedIntervals()
                        if (generation == cacheGeneration) {
                            completed = records
                            historyKnown = true
                        }
                    }
                    publish()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    error = "耳机记录保存或读取失败：${e.message}；保留已知历史，请停止后重新启动以重试"
                    publish()
                }
                if (event.stop != null) {
                    break
                }
            }
        }
        consumer?.invokeOnCompletion { cause ->
            // 生命周期字段只在 Main 清理；完成信号在清理后发布，等待者不再参与清理。
            owner.launch(Dispatchers.Main.immediate) {
                try {
                    monitor.stop() // 消费者异常退出也不能留下系统监听。
                } finally {
                    channel.close()
                    if (events === channel) {
                        active = false
                        events = null
                        consumer = null
                        scope = null
                        val done = stopSignal
                        stopSignal = null
                        if (cause != null) mutableState.value = mutableState.value.copy(
                            running = false, error = "耳机守护结束失败：${cause.message}")
                        done?.complete(Unit)
                    }
                    owner.cancel()
                }
            }
        }
        if (!monitor.start { reading, error -> channel.trySend(Event(reading, error)) }) {
            stop()
        }
    }

    /** Main 同步注销监听，IO 消费者独立完成最后落库；重复停止共享信号。 */
    fun stop(): Deferred<Unit> {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        synchronized(publicationLock) {
            active = false
            mutableState.value = mutableState.value.copy(running = false, reading = null,
                risk = RiskEvaluator().evaluate(0, 0f, mutableState.value.weekMs))
        }
        stopSignal?.let { return it }
        val channel = events ?: return CompletableDeferred(Unit)
        val done = CompletableDeferred<Unit>()
        stopSignal = done
        try {
            monitor.stop()
        } finally {
            channel.trySend(Event(stop = done, stopMono = SystemClock.elapsedRealtime()))
        }
        return done
    }
}
