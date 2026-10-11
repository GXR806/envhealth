package com.GXR806.envhealth.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.retryWhen

/** Service-owned; no UI subscription or Activity lifecycle dependency.
 * Retry only the check/listener, never hardware or an already claimed notification.
 * Coordinator remains responsible for durable at-most-once and stop/generation checks.
 */
class RealtimeReminderListener(private val retryMs: Long = 5_000L) {
    suspend fun run(triggers: Flow<Unit>, check: suspend () -> Unit, failure: (Exception) -> Unit) {
        triggers.retryWhen { cause, _ ->
            if (cause is CancellationException || cause !is Exception) throw cause
            failure(cause)
            delay(retryMs)
            true
        }.conflate().collect {
            try { check() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failure(e)
                // Bound repeated failures; next conflated event/tick rechecks normal policy.
                delay(retryMs)
            }
            currentCoroutineContext().ensureActive()
        }
    }
}
