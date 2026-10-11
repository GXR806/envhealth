package com.GXR806.envhealth.worker

import com.GXR806.envhealth.data.local.datastore.SettingsSnapshot
import com.GXR806.envhealth.data.repository.ReminderStore
import com.GXR806.envhealth.domain.analysis.ReminderPolicy
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** 唯一进程的 Worker/通知动作/清除共享维护锁；非数据库跨系统事务。 */
class MaintenanceGate {
    val mutex = Mutex()
    val blocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val generation = kotlinx.coroutines.flow.MutableStateFlow(0L)
    suspend fun <T> write(expected: Long = generation.value, block: suspend () -> T): T = mutex.withLock {
        check(!blocked.value && expected == generation.value) { "正在维护，请稍后重试" }
        block()
    }
}

fun interface ReminderNotificationSink {
    suspend fun eligible(event: ReminderPolicy.Event): Boolean = true
    /** false 表示没有发送（权限/系统/频道关闭），不得记入分母。 */
    suspend fun send(event: ReminderPolicy.Event): Boolean
}

/** 手动 DI，可在 instrumentation 用独立 Room/DataStore、假时钟/通知 sink。 */
interface ReminderRuntime {
    val gate: MaintenanceGate
    val store: ReminderStore
    val sink: ReminderNotificationSink
    fun now(): Long = System.currentTimeMillis()
    fun diagnostic(type: ReminderPolicy.Type?, reason: String) {}
    suspend fun settings(): SettingsSnapshot
    suspend fun risk(now: Long): ReminderPolicy.LiveRisk
    suspend fun cleanup(now: Long)
}

class ReminderCoordinator(private val runtime: ReminderRuntime) {
    private val reasons = mutableMapOf<ReminderPolicy.Type?, String>()
    private fun diagnostic(type: ReminderPolicy.Type?, reason: String) {
        runtime.diagnostic(type, reason)
        if (reasons.put(type, reason) != reason)
            com.GXR806.envhealth.util.LogX.i("Reminder", "类型=${type?.name ?: "ALL"} 原因=$reason")
    }

    /** Worker performs retention; the service uses remindOnly and never scans/prunes history.
     * guard is a non-suspending service lifetime check, also checked after every suspension
     * preceding notify. Cancellation while waiting for the gate never delays stop.
     */
    suspend fun run(remindOnly: Boolean = false, guard: () -> Boolean = { true }) {
        val generation = runtime.gate.generation.value
        fun active() = guard() && !runtime.gate.blocked.value && generation == runtime.gate.generation.value
        runtime.gate.mutex.withLock {
            if (!active()) { diagnostic(null, "监测停止或维护代次失效"); return@withLock }
            diagnostic(null, "正在检查真实策略（不绕过冷却）")
            val settings = runtime.settings()
            if (!active() || !settings.hasPrivacyConsent || settings.maintenanceState.isNotEmpty()) {
                diagnostic(null, "隐私或维护关闭"); return@withLock
            }
            val now = runtime.now()
            if (!remindOnly) {
                runtime.cleanup(now)
                runtime.store.pruneExpired(now)
            }
            if (!active() || !settings.backgroundMonitoringEnabled) {
                diagnostic(null, "后台关闭或监测停止"); return@withLock
            }
            val metadata = runtime.store.read()
            val baseline = ReminderPolicy.Baselines(settings.noiseThresholdDb,
                settings.volumeThresholdPercent, settings.continuousListeningThresholdMinutes)
            val thresholds = ReminderPolicy.personalized(baseline, metadata.events, now, settings.baselineVersion)
            val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            val silent = ReminderPolicy.isQuiet(local.hour * 60 + local.minute,
                settings.sleepStartHour * 60 + settings.sleepStartMinute,
                settings.sleepEndHour * 60 + settings.sleepEndMinute)
            val risks = ReminderPolicy.risks(runtime.risk(now), thresholds, now)
            for (type in ReminderPolicy.Type.entries) {
                if (type !in risks) { diagnostic(type, "未风险"); continue }
                if (!ReminderPolicy.maySend(now, metadata.lastSent[type], metadata.quietUntil[type])) {
                    diagnostic(type, if (metadata.quietUntil[type]?.let { now < it } == true) "7天静音" else "24小时节流")
                    continue
                }
                val event = ReminderPolicy.Event(UUID.randomUUID().toString(), type, now, silent, settings.baselineVersion)
                if (!active()) return@withLock
                if (!runtime.sink.eligible(event)) { diagnostic(type, "权限或健康频道关闭"); continue }
                // Gate may have been held by a report for some time: never send from its old snapshot.
                if (runtime.settings() != settings || !active()) return@withLock
                val previous = metadata.lastSent[type]
                var sinkInvoked = false
                try {
                    runtime.store.claimSend(event)
                    val current = runtime.settings()
                    val checkAt = runtime.now()
                    val currentRisks = ReminderPolicy.risks(runtime.risk(checkAt), thresholds, checkAt)
                    if (!active() || current != settings || !current.hasPrivacyConsent ||
                        !current.backgroundMonitoringEnabled || current.maintenanceState.isNotEmpty() || type !in currentRisks) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runtime.store.rollbackSend(event, previous) }
                        diagnostic(type, "监测停止或风险设置变化"); return@withLock
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    // No suspension between final lifetime check and the production synchronous notify.
                    if (!active()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runtime.store.rollbackSend(event, previous) }
                        return@withLock
                    }
                    sinkInvoked = true
                    if (runtime.sink.send(event)) {
                        runtime.store.recordSent(event)
                        diagnostic(type, if (silent) "静默 notify 正常返回且事件已保存；不代表横幅可见" else "健康 notify 正常返回且事件已保存；不代表横幅可见")
                    } else {
                        runtime.store.rollbackSend(event, previous)
                        diagnostic(type, "系统未接受发送，已回滚")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancellation before calling sink is a known non-send, including cancellation
                    // after a durable DataStore claim. Once sink is invoked the result may be unknown.
                    if (!sinkInvoked) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        runtime.store.rollbackSend(event, previous)
                    }
                    throw e
                }
            }
        }
    }
}
