package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.worker.RealtimeReminderListener
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/** Simulated owner/flows, not Android background scheduling or system notification tests. */
class RealtimeReminderListenerTest {
    @Test fun noUiSubscriber_serviceFlowKeepsCheckingFreshRisk() = runBlocking {
        val events = Channel<Unit>(Channel.UNLIMITED)
        var now = 1_000_000L
        var readingAt = now - ReminderPolicy.FRESH_MS - 1
        var running = true
        val checks = Channel<Boolean>(Channel.UNLIMITED)
        val job = launch {
            RealtimeReminderListener(1).run(events.receiveAsFlow(), {
                val risks = ReminderPolicy.risks(ReminderPolicy.LiveRisk(volume = 85f,
                    volumeAt = readingAt, connectedAndPlaying = running), ReminderPolicy.Baselines(), now)
                checks.send(ReminderPolicy.Type.VOLUME in risks)
            }, { fail("unexpected failure") })
        }
        events.send(Unit)
        assertFalse(withTimeout(1_000) { checks.receive() })
        // A simulated monitor update after 5 seconds, with zero UI collectors.
        now += 5_000; readingAt = now
        events.send(Unit)
        assertTrue(withTimeout(1_000) { checks.receive() })
        running = false
        events.send(Unit)
        assertFalse(withTimeout(1_000) { checks.receive() })
        job.cancelAndJoin()
    }

    @Test fun checkFailure_doesNotPermanentlyEndListener() = runBlocking {
        val events = Channel<Unit>(Channel.UNLIMITED)
        val failed = CompletableDeferred<Unit>()
        val recovered = CompletableDeferred<Unit>()
        var calls = 0
        val job = launch {
            RealtimeReminderListener(1).run(events.receiveAsFlow(), {
                if (++calls == 1) error("temporary read failure")
                recovered.complete(Unit)
            }, { failed.complete(Unit) })
        }
        events.send(Unit); withTimeout(1_000) { failed.await() }
        events.send(Unit); withTimeout(1_000) { recovered.await() }
        assertTrue(job.isActive)
        job.cancelAndJoin()
    }

    @Test fun upstreamFailure_resubscribesWithoutUi() = runBlocking {
        var subscriptions = 0
        var failures = 0
        var checks = 0
        val triggers = flow {
            if (++subscriptions == 1) error("temporary upstream failure")
            emit(Unit)
        }
        RealtimeReminderListener(1).run(triggers, { checks++ }, { failures++ })
        assertEquals(2, subscriptions); assertEquals(1, failures); assertEquals(1, checks)
    }

    @Test fun cancellation_neverRetriesOrChecksAfterStop() = runBlocking {
        var checks = 0
        var failures = 0
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            RealtimeReminderListener(1).run(flowOf(Unit), {
                checks++; entered.complete(Unit); awaitCancellation()
            }, { failures++ })
        }
        entered.await(); withTimeout(1_000) { job.cancelAndJoin() }
        assertEquals(1, checks); assertEquals(0, failures)
    }
}
