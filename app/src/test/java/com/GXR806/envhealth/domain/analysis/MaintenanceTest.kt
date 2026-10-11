package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.worker.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** 纯边界测试，不实例化 Android Context/生产 Room/通知。 */
class MaintenanceTest {
    private class Ports : MaintenanceOperations {
        override val gate = MaintenanceGate()
        val trace = mutableListOf<String>()
        var state = ""
        var enabled = true
        var rows = 1
        var failDelete = false
        var stopBarrier: CompletableDeferred<Unit>? = null
        override suspend fun state(value: String) { state = value; trace += value }
        override suspend fun cancelWork() { trace += "cancel" }
        override suspend fun stopWriters() {
            assertTrue(gate.mutex.tryLock()); gate.mutex.unlock()
            trace += "stop"
            stopBarrier?.await()
            rows++ // 最后一次 writer 写入在 delete 前完成
        }
        override suspend fun deleteDatabase() { if (failDelete) error("disk failure"); trace += "delete"; rows = 0 }
        override suspend fun clearCaches() { trace += "cache" }
        override suspend fun resetSettings() { enabled = false; state = ""; trace += "reset" }
        override suspend fun background(enabled: Boolean) { this.enabled = enabled }
        override fun cancelNotifications() { trace += "notifications" }
        override fun restoreCleanupWork() { trace += "restore" }
    }
    @Test fun clear_ordersJoinDeleteReset_andIsReentrant() = runBlocking {
        val p = Ports(); val m = DataMaintenance(p)
        m.clearAll()
        assertEquals(listOf("clearing", "cancel", "stop", "delete", "cache", "notifications", "reset", "restore"), p.trace)
        assertEquals(0, p.rows); assertFalse(p.enabled); assertFalse(p.gate.blocked.value)
        m.clearAll(); assertEquals(0, p.rows); assertEquals(2L, p.gate.generation.value)
    }
    @Test fun failedClear_blocksThenRetryRecovers() = runBlocking {
        val p = Ports(); val m = DataMaintenance(p)
        p.failDelete = true
        assertTrue(runCatching { m.clearAll() }.isFailure)
        assertTrue(p.gate.blocked.value); assertEquals("error", p.state)
        assertTrue(runCatching { p.gate.write { p.rows++ } }.isFailure)
        p.failDelete = false; m.clearAll()
        assertEquals(0, p.rows); assertFalse(p.gate.blocked.value)
    }
    @Test fun disable_doesNotReturnBeforeWriterJoined() = runBlocking {
        val p = Ports(); val m = DataMaintenance(p)
        p.stopBarrier = CompletableDeferred()
        val job = launch { m.setBackground(false) }
        yield()
        assertFalse(p.enabled); assertTrue(job.isActive)
        p.stopBarrier!!.complete(Unit); job.join()
        assertTrue(p.trace.contains("notifications"))
    }
    @Test fun staleWriteRejected_andClearContinuesOnCallerCancellation() = runBlocking {
        val p = Ports(); val m = DataMaintenance(p)
        val generation = p.gate.generation.value
        p.stopBarrier = CompletableDeferred()
        val job = launch { m.clearAll() }
        yield(); assertTrue(p.gate.blocked.value)
        job.cancel(); p.stopBarrier!!.complete(Unit); job.join()
        assertEquals(0, p.rows); assertFalse(p.gate.blocked.value)
        assertTrue(runCatching { p.gate.write(generation) { p.rows++ } }.isFailure)
    }
}
