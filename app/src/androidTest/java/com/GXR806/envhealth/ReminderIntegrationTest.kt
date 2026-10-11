package com.GXR806.envhealth

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.SynchronousExecutor
import com.GXR806.envhealth.data.local.datastore.SettingsSnapshot
import com.GXR806.envhealth.data.local.room.EnvHealthDatabase
import com.GXR806.envhealth.data.local.room.entity.NoiseSampleEntity
import com.GXR806.envhealth.data.repository.ReminderStore
import com.GXR806.envhealth.domain.analysis.ReminderPolicy
import com.GXR806.envhealth.worker.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReminderIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: EnvHealthDatabase
    private lateinit var file: File
    private lateinit var owner: CoroutineScope
    private lateinit var runtime: FakeRuntime
    private lateinit var settings: com.GXR806.envhealth.data.repository.SettingsRepository
    private lateinit var wm: WorkManager
    private class FakeRuntime(val db: EnvHealthDatabase, override val store: ReminderStore) : ReminderRuntime {
        override val gate = MaintenanceGate()
        var snapshot = SettingsSnapshot(backgroundMonitoringEnabled = true, privacyConsentVersion = SettingsSnapshot.PRIVACY_POLICY_VERSION)
        var time = localTime(2027, 1, 15, 12, 0)
        var permitted = true
        var live: ReminderPolicy.LiveRisk? = null
        val sentEvents = CopyOnWriteArrayList<ReminderPolicy.Event>()
        val accepted = AtomicInteger()
        val completed = AtomicInteger()
        override val sink = object : ReminderNotificationSink {
            override suspend fun eligible(event: ReminderPolicy.Event) = permitted
            override suspend fun send(event: ReminderPolicy.Event): Boolean {
                // durable claim MUST precede side effect
                check(store.read().lastSent[event.type] == event.sentAt)
                if (!permitted) return false
                sentEvents += event
                accepted.incrementAndGet()
                return true
            }
        }
        override fun now() = time
        override suspend fun settings() = snapshot
        override suspend fun risk(now: Long) = live ?: ReminderPolicy.LiveRisk(noiseDb = 95f, noiseAt = now)
        override suspend fun cleanup(now: Long) { db.noiseSampleDao().deleteBeforeTime(now - 7 * ReminderPolicy.DAY_MS); completed.incrementAndGet() }
    }
    companion object {
        // 与 Coordinator 相同的本地时区；指定日期/时刻，不依赖测试运行当天。
        private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
            LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    @Test fun privacyVersion_persistsAndResetRevokes_andWorkerDoesNothingBeforeConsent() = runBlocking {
        assertFalse(settings.snapshot.first().hasPrivacyConsent)
        assertTrue(runCatching { settings.setBackgroundMonitoringEnabled(true) }.isFailure)
        runtime.snapshot = SettingsSnapshot()
        ReminderCoordinator(runtime).run()
        assertEquals(0, runtime.completed.get())
        assertEquals(0, runtime.accepted.get())
        settings.acceptPrivacyPolicy()
        assertEquals(SettingsSnapshot.PRIVACY_POLICY_VERSION, settings.snapshot.first().privacyConsentVersion)
        // Persistence is read back from DataStore, independent of UI defaults.
        assertTrue(settings.snapshot.first().hasPrivacyConsent)
        assertFalse(settings.snapshot.first().backgroundMonitoringEnabled)
        runtime.snapshot = settings.snapshot.first()
        ReminderCoordinator(runtime).run()
        assertEquals(1, runtime.completed.get())
        assertEquals(0, runtime.accepted.get())
        settings.resetAll()
        assertFalse(settings.snapshot.first().hasPrivacyConsent)
        runtime.snapshot = settings.snapshot.first()
        ReminderCoordinator(runtime).run()
        assertEquals(1, runtime.completed.get())
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, EnvHealthDatabase::class.java).build()
        owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "reminder-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = owner, produceFile = { file })
        settings = com.GXR806.envhealth.data.repository.SettingsRepository(context, store)
        runtime = FakeRuntime(db, ReminderStore(store))
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == ReminderWorker::class.java.name) ReminderWorker(appContext, workerParameters, runtime) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        wm = WorkManager.getInstance(context)
    }
    @After fun teardown() {
        wm.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
        runBlocking { owner.coroutineContext[Job]?.cancelAndJoin() }
        db.close(); file.delete()
    }
    private fun awaitRuns(count: Int) = runBlocking {
        withTimeout(10_000) { while (runtime.completed.get() < count || wm.getWorkInfosByTag("isolated").get().any { it.state == WorkInfo.State.RUNNING }) delay(20) }
    }
    @Test fun periodicDriver_throttles24Hours_andCleanupOnlyWhenDisabled() = runBlocking {
        db.noiseSampleDao().insert(NoiseSampleEntity(timestamp = runtime.time - 8 * ReminderPolicy.DAY_MS, leqDb = 40f, maxDb = 40f, peakDb = 40f, isEvent = false))
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).addTag("isolated").build()
        wm.enqueueUniquePeriodicWork("isolated-reminder", ExistingPeriodicWorkPolicy.KEEP, request).result.get()
        awaitRuns(1)
        assertEquals(1, runtime.accepted.get())
        assertEquals(0, db.noiseSampleDao().getCount())
        val driver = checkNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setPeriodDelayMet(request.id); awaitRuns(2)
        assertEquals(1, runtime.accepted.get())
        runtime.time += ReminderPolicy.DAY_MS
        driver.setPeriodDelayMet(request.id); awaitRuns(3)
        assertEquals(2, runtime.accepted.get())
        runtime.snapshot = runtime.snapshot.copy(backgroundMonitoringEnabled = false)
        runtime.time += ReminderPolicy.DAY_MS
        driver.setPeriodDelayMet(request.id); awaitRuns(4)
        assertEquals(2, runtime.accepted.get())
    }
    @Test fun settingsPersist_atomicQuietTimeValidation_andResetErasesFeedback() = runBlocking {
        settings.setThemeMode(SettingsSnapshot.THEME_MODE_DARK)
        settings.acceptPrivacyPolicy()
        settings.setBackgroundMonitoringEnabled(true)
        settings.setNoiseThresholdDb(80f)
        settings.setVolumeThresholdPercent(75f)
        settings.setContinuousListeningThresholdMinutes(45)
        settings.setDbCalibrationOffset(90f)
        settings.setLuxEstimationFactor(150f)
        settings.setLastCleanupTimestamp(runtime.time)
        settings.setQuietTimes(23, 30, 6, 15)
        val saved = settings.snapshot.first()
        assertEquals(SettingsSnapshot.THEME_MODE_DARK, saved.themeMode)
        assertTrue(runCatching { settings.setThemeMode("INVALID") }.isFailure)
        assertEquals(3L, saved.baselineVersion)
        assertEquals(23, saved.sleepStartHour); assertEquals(15, saved.sleepEndMinute)
        assertTrue(runCatching { settings.setNoiseThresholdDb(Float.NaN) }.isFailure)
        assertTrue(runCatching { settings.setQuietTimes(24, 0, 7, 0) }.isFailure)
        assertEquals(saved, settings.snapshot.first())
        runtime.snapshot = saved
        runtime.time = localTime(2027, 1, 15, 23, 45)
        ReminderCoordinator(runtime).run()
        val muted = runtime.sentEvents.single()
        assertTrue(muted.silent)
        assertNotNull(settings.reminderStore.claimAction(muted.id, muted.type, ReminderPolicy.Action.MUTE, runtime.time))
        // 另一类型保存真实有效反馈，连同 claim、静音截止时间及节流一起跨 owner 重开。
        val feedback = ReminderPolicy.Event(UUID.randomUUID().toString(), ReminderPolicy.Type.VOLUME,
            runtime.time, false, saved.baselineVersion)
        settings.reminderStore.recordSent(feedback)
        assertNotNull(settings.reminderStore.claimAction(feedback.id, feedback.type,
            ReminderPolicy.Action.LOWER_VOLUME, runtime.time))
        settings.reminderStore.markEffective(feedback.id)
        val savedReminders = settings.reminderStore.read()
        assertEquals(runtime.time, savedReminders.lastSent[muted.type])
        assertEquals(runtime.time + ReminderPolicy.QUIET_MS, savedReminders.quietUntil[muted.type])
        assertTrue(savedReminders.events.single { it.id == feedback.id }.effective)
        val feedbackAt = runtime.time + ReminderPolicy.DAY_MS
        val savedFeedback = ReminderPolicy.feedback(savedReminders.events, feedback.type, feedbackAt, saved.baselineVersion)
        assertEquals(ReminderPolicy.Feedback(1, 1), savedFeedback)

        // 真正释放旧 DataStore（包括文件 owner），再以新 scope 打开同一个随机临时文件。
        owner.coroutineContext[Job]!!.cancelAndJoin()
        owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = PreferenceDataStoreFactory.create(scope = owner, produceFile = { file })
        settings = com.GXR806.envhealth.data.repository.SettingsRepository(context, reopened)
        assertEquals(saved, settings.snapshot.first())
        assertEquals(SettingsSnapshot.THEME_MODE_DARK, settings.snapshot.first().themeMode)
        val restored = settings.reminderStore.read()
        assertEquals(savedReminders, restored)
        assertEquals(savedFeedback, ReminderPolicy.feedback(restored.events, feedback.type, feedbackAt, saved.baselineVersion))
        assertNull(settings.reminderStore.claimAction(muted.id, muted.type, ReminderPolicy.Action.MUTE, runtime.time))
        assertNull(settings.reminderStore.claimAction(feedback.id, feedback.type, ReminderPolicy.Action.LOWER_VOLUME, runtime.time))
        val muteUntil = checkNotNull(restored.quietUntil[muted.type])
        runtime = FakeRuntime(db, settings.reminderStore).apply { snapshot = saved; time = muteUntil - 1 }
        ReminderCoordinator(runtime).run()
        assertTrue(runtime.sentEvents.isEmpty()) // 同 type 的 MUTE 七天截止前 1ms 仍阻断。
        runtime.time = muteUntil
        ReminderCoordinator(runtime).run()
        assertEquals(1, runtime.sentEvents.size) // 恰好七天恢复，同 type 的 24h 节流已过。
        assertEquals(muted.type, runtime.sentEvents.single().type)
        // 仅向隔离临时文件注入非法持久值，读取回退跟随系统。
        reopened.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")] = "INVALID"
            }
        }
        assertEquals(SettingsSnapshot.THEME_MODE_SYSTEM, settings.snapshot.first().themeMode)
        settings.resetAll()
        assertEquals(SettingsSnapshot.THEME_MODE_SYSTEM, settings.snapshot.first().themeMode)
        assertEquals(SettingsSnapshot(), settings.snapshot.first())
        assertTrue(settings.reminderStore.read().events.isEmpty())
        assertTrue(settings.reminderStore.read().lastSent.isEmpty())
        assertTrue(settings.reminderStore.read().quietUntil.isEmpty())
    }

    @Test fun retention_endBoundariesProtectOngoingAndCascadeReports() = runBlocking {
        val cutoff = runtime.time - 90 * ReminderPolicy.DAY_MS
        val sessions = db.sleepSessionDao()
        val expired = sessions.insert(com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity(startTime = cutoff - 1000, endTime = cutoff - 1, type = "MANUAL", status = "COMPLETED"))
        val boundary = sessions.insert(com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity(startTime = cutoff - 1000, endTime = cutoff, type = "MANUAL", status = "COMPLETED"))
        val ongoing = sessions.insert(com.GXR806.envhealth.data.local.room.entity.SleepSessionEntity(startTime = cutoff - 1000, endTime = null, type = "MANUAL", status = "ONGOING"))
        db.sleepReportDao().insert(com.GXR806.envhealth.data.local.room.entity.SleepReportEntity(sessionId = expired, noiseScore = 80f, lightScore = 80f, regularityScore = null, totalScore = 80f, summary = "保留边界测试报告"))
        assertEquals(1, sessions.deleteCompletedBeforeEnd(cutoff))
        assertNull(sessions.getById(expired)); assertNull(db.sleepReportDao().getBySessionId(expired))
        assertNotNull(sessions.getById(boundary)); assertNotNull(sessions.getById(ongoing))
        val headphones = db.headphoneUsageDao()
        val old = headphones.insert(com.GXR806.envhealth.data.local.room.entity.HeadphoneUsageEntity(startTime = cutoff - 1000, endTime = cutoff - 1, avgVolumePercent = 50f, maxVolumePercent = 50f, deviceType = "WIRED"))
        val live = headphones.insert(com.GXR806.envhealth.data.local.room.entity.HeadphoneUsageEntity(startTime = cutoff - 1000, endTime = null, avgVolumePercent = 50f, maxVolumePercent = 50f, deviceType = "WIRED"))
        assertEquals(1, headphones.deleteCompletedBeforeEnd(cutoff))
        assertNull(headphones.getById(old)); assertNotNull(headphones.getById(live))
    }

    @Test fun permissionBlocked_doesNotClaimOrCount() = runBlocking {
        runtime.snapshot = runtime.snapshot.copy(sleepStartHour = 23, sleepStartMinute = 30,
            sleepEndHour = 6, sleepEndMinute = 15)
        runtime.time = localTime(2027, 1, 15, 23, 45)
        runtime.permitted = false
        ReminderCoordinator(runtime).run()
        assertTrue(runtime.store.read().events.isEmpty())
        assertTrue(runtime.store.read().lastSent.isEmpty())
        assertTrue(runtime.sentEvents.isEmpty())
        runtime.permitted = true
        ReminderCoordinator(runtime).run()
        assertEquals(1, runtime.accepted.get())
        assertTrue(runtime.sentEvents.last().silent) // 午夜前属于跨午夜静默窗口。
        runtime.time = localTime(2027, 1, 17, 0, 30) // 已超过24h节流，午夜后仍静默。
        ReminderCoordinator(runtime).run()
        assertEquals(2, runtime.accepted.get())
        assertTrue(runtime.sentEvents.last().silent)
        runtime.time = localTime(2027, 1, 18, 12, 0)
        ReminderCoordinator(runtime).run()
        assertEquals(3, runtime.accepted.get())
        assertFalse(runtime.sentEvents.last().silent)
        assertEquals(runtime.sentEvents.toList(), runtime.store.read().events)
    }
    @Test fun clearChain_joinBeforeDelete_reentrantAndRejectsQueuedWrites() = runBlocking {
        val steps = mutableListOf<String>()
        val generation = runtime.gate.generation.value
        val operations = object : MaintenanceOperations {
            override val gate = runtime.gate
            override suspend fun state(value: String) { runtime.snapshot = runtime.snapshot.copy(maintenanceState = value); steps += value }
            override suspend fun cancelWork() { steps += "cancel" }
            override suspend fun stopWriters() {
                // 停止存储不能在持有同一维护锁时等待。
                assertTrue(gate.mutex.tryLock()); gate.mutex.unlock()
                steps += "join"
                db.noiseSampleDao().insert(NoiseSampleEntity(timestamp = runtime.time, leqDb = 40f, maxDb = 40f, peakDb = 40f, isEvent = false))
            }
            override suspend fun deleteDatabase() { steps += "delete"; withContext(Dispatchers.IO) { db.clearAllTables() } }
            override suspend fun clearCaches() { steps += "cache" }
            override suspend fun resetSettings() { runtime.snapshot = SettingsSnapshot(); steps += "reset" }
            override suspend fun background(enabled: Boolean) { runtime.snapshot = runtime.snapshot.copy(backgroundMonitoringEnabled = enabled) }
            override fun cancelNotifications() { steps += "notifications" }
            override fun restoreCleanupWork() { assertFalse(runtime.snapshot.backgroundMonitoringEnabled); steps += "cleanup-only" }
        }
        val maintenance = DataMaintenance(operations)
        maintenance.clearAll()
        assertEquals(listOf("clearing", "cancel", "join", "delete", "cache", "notifications", "reset", "cleanup-only"), steps)
        assertEquals(0, db.noiseSampleDao().getCount())
        assertTrue(runCatching { runtime.gate.write(generation) { db.noiseSampleDao().insert(NoiseSampleEntity(timestamp = runtime.time, leqDb = 40f, maxDb = 40f, peakDb = 40f, isEvent = false)) } }.isFailure)
        maintenance.clearAll()
        assertEquals(0, db.noiseSampleDao().getCount())
        assertFalse(runtime.gate.blocked.value)
        assertEquals(96f, runtime.snapshot.dbCalibrationOffset)
    }

    @Test fun falseSink_rollsBackClaim_unknownFailureKeepsThrottleWithoutDenominator() = runBlocking {
        var fail = false
        val testRuntime = object : ReminderRuntime by runtime {
            override val sink = ReminderNotificationSink {
                if (fail) throw IllegalStateException("unknown notification result")
                false
            }
        }
        ReminderCoordinator(testRuntime).run()
        assertTrue(runtime.store.read().lastSent.isEmpty())
        fail = true
        assertTrue(runCatching { ReminderCoordinator(testRuntime).run() }.isFailure)
        assertEquals(runtime.time, runtime.store.read().lastSent[ReminderPolicy.Type.NOISE])
        assertTrue(runtime.store.read().events.isEmpty())
    }

    private fun volumeRisk() {
        runtime.live = ReminderPolicy.LiveRisk(volume = 85f, volumeAt = runtime.time, connectedAndPlaying = true)
    }

    @Test fun realtimeVolume_rapidUpdatesAndConcurrentWorker_sendOnceWithoutCleanup() = runBlocking {
        volumeRisk()
        val coordinator = ReminderCoordinator(runtime)
        repeat(20) { coordinator.run(remindOnly = true) }
        assertEquals(0, runtime.completed.get())
        coroutineScope {
            launch { coordinator.run(remindOnly = true) }
            launch { ReminderCoordinator(runtime).run() }
        }
        assertEquals(1, runtime.accepted.get())
        assertEquals(ReminderPolicy.Type.VOLUME, runtime.sentEvents.single().type)
        assertEquals(1, runtime.store.read().events.size)
    }

    @Test fun realtimeVolume_cooldownMuteAndSilentAreShared() = runBlocking {
        volumeRisk()
        val coordinator = ReminderCoordinator(runtime)
        coordinator.run(remindOnly = true)
        coordinator.run(remindOnly = true)
        assertEquals(1, runtime.accepted.get())
        val event = runtime.sentEvents.single()
        runtime.store.claimAction(event.id, event.type, ReminderPolicy.Action.MUTE, runtime.time)
        runtime.time += ReminderPolicy.QUIET_MS - 1
        volumeRisk()
        coordinator.run(remindOnly = true)
        assertEquals(1, runtime.accepted.get())
        runtime.time += 1
        volumeRisk()
        runtime.snapshot = runtime.snapshot.copy(sleepStartHour = 0, sleepEndHour = 0)
        coordinator.run(remindOnly = true)
        assertEquals(2, runtime.accepted.get())
        assertTrue(runtime.sentEvents.last().silent)
    }

    @Test fun realtimeVolume_channelBlockedThenFalseSink_neverCounts() = runBlocking {
        volumeRisk()
        runtime.permitted = false
        ReminderCoordinator(runtime).run(remindOnly = true)
        assertTrue(runtime.store.read().lastSent.isEmpty())
        runtime.permitted = true
        val rejected = object : ReminderRuntime by runtime {
            override val sink = ReminderNotificationSink { false }
        }
        ReminderCoordinator(rejected).run(remindOnly = true)
        assertTrue(runtime.store.read().lastSent.isEmpty())
        assertTrue(runtime.store.read().events.isEmpty())
    }

    @Test fun realtimeVolume_stopPrivacyAndBackgroundBlock() = runBlocking {
        volumeRisk()
        ReminderCoordinator(runtime).run(remindOnly = true, guard = { false })
        runtime.snapshot = runtime.snapshot.copy(backgroundMonitoringEnabled = false)
        ReminderCoordinator(runtime).run(remindOnly = true)
        runtime.snapshot = runtime.snapshot.copy(backgroundMonitoringEnabled = true, privacyConsentVersion = 0)
        ReminderCoordinator(runtime).run(remindOnly = true)
        assertEquals(0, runtime.completed.get())
        assertEquals(0, runtime.accepted.get())
        assertTrue(runtime.store.read().lastSent.isEmpty())
    }

    @Test fun realtimeVolume_stopWhileWaitingForGate_andGenerationInvalidation() = runBlocking {
        volumeRisk()
        var running = true
        runtime.gate.mutex.lock()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            ReminderCoordinator(runtime).run(remindOnly = true) { running }
        }
        running = false
        runtime.gate.generation.value++
        runtime.gate.mutex.unlock()
        job.join()
        assertEquals(0, runtime.accepted.get())
        assertTrue(runtime.store.read().lastSent.isEmpty())
    }

    @Test fun realtimeVolume_settingsRevokedAfterClaim_rollsBackBeforeNotify() = runBlocking {
        volumeRisk()
        var reads = 0
        val revoked = object : ReminderRuntime by runtime {
            override suspend fun settings(): SettingsSnapshot {
                reads++
                return if (reads >= 3) runtime.snapshot.copy(backgroundMonitoringEnabled = false) else runtime.snapshot
            }
        }
        ReminderCoordinator(revoked).run(remindOnly = true)
        assertEquals(0, runtime.accepted.get())
        assertTrue(runtime.store.read().lastSent.isEmpty())
    }

    @Test fun realtimeVolume_cancelGateWait_doesNotBlockStopOrClaim() = runBlocking {
        volumeRisk()
        runtime.gate.mutex.lock()
        try {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                ReminderCoordinator(runtime).run(remindOnly = true)
            }
            withTimeout(1_000) { job.cancelAndJoin() }
            assertTrue(runtime.store.read().lastSent.isEmpty())
            assertEquals(0, runtime.accepted.get())
        } finally { runtime.gate.mutex.unlock() }
    }

    @Test fun realtimeVolume_stopAfterClaim_guardRollsBack() = runBlocking {
        volumeRisk()
        var reads = 0
        var running = true
        val stopped = object : ReminderRuntime by runtime {
            override suspend fun settings(): SettingsSnapshot {
                if (++reads >= 3) running = false
                return runtime.snapshot
            }
        }
        ReminderCoordinator(stopped).run(remindOnly = true) { running }
        assertEquals(0, runtime.accepted.get())
        assertTrue(runtime.store.read().lastSent.isEmpty())
    }

    @Test fun serviceOwnedListener_backgroundWithoutUi_recoversAndSharesCooldown() = runBlocking {
        val triggers = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val checked = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        var failedOnce = false
        val diagnostics = mutableListOf<String>()
        val observed = object : ReminderRuntime by runtime {
            override fun diagnostic(type: ReminderPolicy.Type?, reason: String) { diagnostics += reason }
            override suspend fun risk(now: Long): ReminderPolicy.LiveRisk {
                if (!failedOnce) { failedOnce = true; error("temporary risk read") }
                return runtime.risk(now)
            }
        }
        volumeRisk()
        val job = launch {
            RealtimeReminderListener(1).run(triggers.receiveAsFlow(), {
                try { ReminderCoordinator(observed).run(remindOnly = true) }
                finally { checked.send(Unit) }
            }, {})
        }
        triggers.send(Unit); withTimeout(1_000) { checked.receive() }
        // Simulated background: no UI subscriber, monitor publishes a fresh 5s update.
        runtime.time += 5_000; volumeRisk()
        triggers.send(Unit); withTimeout(1_000) { checked.receive() }
        assertEquals(1, runtime.accepted.get())
        triggers.send(Unit); withTimeout(1_000) { checked.receive() }
        assertEquals(1, runtime.accepted.get())
        assertTrue(diagnostics.any { it == "24小时节流" })
        job.cancelAndJoin()
        assertEquals(0, runtime.completed.get())
    }

    @Test fun channelDiagnostics_andFalseSink_doNotInventSentEvent() = runBlocking {
        volumeRisk()
        val reasons = mutableListOf<String>()
        var eligible = false
        val observed = object : ReminderRuntime by runtime {
            override fun diagnostic(type: ReminderPolicy.Type?, reason: String) { reasons += reason }
            override val sink = object : ReminderNotificationSink {
                override suspend fun eligible(event: ReminderPolicy.Event) = eligible
                override suspend fun send(event: ReminderPolicy.Event) = false
            }
        }
        ReminderCoordinator(observed).run(remindOnly = true)
        assertTrue(reasons.contains("权限或健康频道关闭"))
        assertTrue(runtime.store.read().lastSent.isEmpty())
        eligible = true
        ReminderCoordinator(observed).run(remindOnly = true)
        assertTrue(reasons.contains("系统未接受发送，已回滚"))
        assertTrue(runtime.store.read().events.isEmpty())
        assertTrue(runtime.store.read().lastSent.isEmpty())
    }

    @Test fun runtimeFreshAndStopped_rejectThenAcceptAtFiveSecondUpdate() = runBlocking {
        runtime.live = ReminderPolicy.LiveRisk(volume = 85f, volumeAt = runtime.time - ReminderPolicy.FRESH_MS - 1,
            connectedAndPlaying = true)
        ReminderCoordinator(runtime).run(remindOnly = true)
        assertEquals(0, runtime.accepted.get())
        runtime.time += 5_000
        runtime.live = ReminderPolicy.LiveRisk(volume = 85f, volumeAt = runtime.time, connectedAndPlaying = false)
        ReminderCoordinator(runtime).run(remindOnly = true)
        assertEquals(0, runtime.accepted.get())
        volumeRisk()
        ReminderCoordinator(runtime).run(remindOnly = true)
        assertEquals(1, runtime.accepted.get())
    }

    @Test fun maintenanceGate_rejectsStaleWrite_andActionReplay() = runBlocking {
        val generation = runtime.gate.generation.value
        runtime.gate.blocked.value = true
        ReminderCoordinator(runtime).run()
        assertEquals(0, runtime.accepted.get())
        runtime.gate.generation.value++
        runtime.gate.blocked.value = false
        assertTrue(runCatching { runtime.gate.write(generation) { error("must not write") } }.isFailure)
        ReminderCoordinator(runtime).run()
        val event = runtime.store.read().events.single()
        assertNotNull(runtime.store.claimAction(event.id, event.type, ReminderPolicy.Action.LATER, runtime.time))
        assertNull(runtime.store.claimAction(event.id, event.type, ReminderPolicy.Action.LOWER_VOLUME, runtime.time))
    }
}
