package com.GXR806.envhealth.data.sensor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BoundedSampleBatchTest {
    @Test fun threeSamplesCommitTogether() = runBlocking {
        val saved = mutableListOf<List<Int>>()
        val batch = BoundedSampleBatch<Int>({ 0L }) { saved.add(it) }
        batch.offer(1); batch.offer(2)
        assertTrue(saved.isEmpty())
        batch.offer(3)
        assertEquals(listOf(listOf(1, 2, 3)), saved)
        batch.flush(); assertEquals(1, saved.size)
    }
    @Test fun ninetySecondsAndTailFlush() = runBlocking {
        var now = 0L
        val saved = mutableListOf<List<Int>>()
        val batch = BoundedSampleBatch<Int>({ now }) { saved.add(it) }
        batch.offer(1); now = 89_999; batch.flushIfDue(); assertTrue(saved.isEmpty())
        now = 90_000; batch.flushIfDue()
        batch.offer(2); batch.flush()
        assertEquals(listOf(listOf(1), listOf(2)), saved)
    }
    @Test fun failureRetainsThreeRejectsGrowthAndExplicitRetry() = runBlocking {
        var fail = true
        val saved = mutableListOf<List<Int>>()
        val batch = BoundedSampleBatch<Int>({ 0L }) { if (fail) error("fake database"); saved.add(it) }
        batch.offer(1); batch.offer(2)
        try { batch.offer(3); fail("expected") } catch (_: IllegalStateException) { }
        assertTrue(batch.failed)
        try { batch.offer(4); fail("expected") } catch (_: IllegalStateException) { }
        fail = false; batch.flush()
        assertEquals(listOf(listOf(1, 2, 3)), saved)
        assertFalse(batch.failed)
    }
    @Test fun cancellationCannotSplitCommitAndRemoval() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val saved = mutableListOf<List<Int>>()
        val batch = BoundedSampleBatch<Int>({ 0L }) {
            entered.complete(Unit); finish.await(); saved.add(it)
        }
        batch.offer(1); batch.offer(2)
        val writer = launch { batch.offer(3) }
        entered.await(); writer.cancel(); finish.complete(Unit); writer.join()
        batch.flush()
        assertEquals(listOf(listOf(1, 2, 3)), saved)
    }
    @Test fun discardAfterJoinedWriterDoesNotResurrect() = runBlocking {
        val saved = mutableListOf<List<Int>>()
        val batch = BoundedSampleBatch<Int>({ 0L }) { saved.add(it) }
        batch.offer(1); batch.discard(); batch.flush()
        assertTrue(saved.isEmpty())
    }
}
