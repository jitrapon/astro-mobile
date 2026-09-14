package io.jitrapon.astro.data.query

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Collapses concurrent requests for the same key onto one execution: the first caller starts the
 * work, everyone who asks while it is running waits on that same call, and all of them receive its
 * one result.
 *
 * Deduplication is the point, but *where the work runs* is what makes it correct. The work is
 * hosted on [scope] — the layer's own scope — and never on a caller's:
 * - `withContext` would run it inside whichever caller happened to arrive first, so a second caller
 *   would have nothing to join and would start a duplicate execution;
 * - it would also tie the work's lifetime to that caller's, so the caller walking away would cancel
 *   an execution other callers are still waiting on.
 *
 * The runner is deliberately **not** reference-counted. An execution in flight when its last waiter
 * leaves runs to completion and publishes its result, because the next caller for that key is
 * overwhelmingly likely to arrive while it is still running — a re-entered screen, a resumed app —
 * and cancelling it would trade a nearly-free join for a fresh round trip.
 *
 * The deduplication mechanism — one shared awaitable per key, released when that execution
 * completes rather than when a waiter leaves — follows Store5's fetcher controller, read as a
 * reference implementation. Nothing of it is vendored: the structure here is its own, and the
 * cancellation contract it upholds is this project's.
 *
 * @param K what an execution is deduplicated under. Two keys that differ in any way are two
 *   different executions, so a caller must key on the whole request identity and never on part of
 *   it.
 * @param V what an execution produces.
 * @param scope the scope executions are hosted on. Its cancellation is what ends them; nothing here
 *   ends one otherwise. A scope with a `SupervisorJob` is expected, so one failed execution does
 *   not tear down the others.
 */
internal class SingleFlightRunner<K : Any, V>(private val scope: CoroutineScope) {

    /**
     * Guards [inFlight]. Held across find-or-create so two callers arriving together cannot both
     * conclude they are first, and across removal so a completing execution cannot race a caller
     * about to join it.
     *
     * A [Mutex] rather than a lock or a `synchronized` block: it suspends instead of blocking a
     * thread, which is what allows the removal inside an execution to wait for a caller that is
     * mid-creation without deadlocking either.
     */
    private val guard = Mutex()

    /** One entry per key currently executing. Only ever read or written under [guard]. */
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    /**
     * Returns the result of the execution filed under [key], starting one from [work] if none is
     * running.
     *
     * [work] is used only when this call is the one that starts the execution. A caller that joins
     * an execution already in flight receives that execution's result and its own [work] is never
     * invoked — so every caller for one key must pass work that means the same thing, which is the
     * assumption keying on the whole request identity discharges.
     *
     * Cancelling this call abandons the wait, not the execution: the result still lands for the
     * other waiters and the key is still released when it does. The thrown `CancellationException`
     * propagates rather than being turned into a value, so a caller that has moved on is never
     * handed a failure.
     */
    suspend fun runOnce(key: K, work: suspend () -> V): V {
        val flight = guard.withLock { inFlight[key] ?: registerFlight(key, work) }
        // Started outside the critical section above, and lazily so that it cannot begin inside it:
        // the execution's own bookkeeping takes `guard`, and starting it while that lock is held
        // would have it wait on the very caller that is creating it. Starting an execution that is
        // already running — which is what every joining caller does here — is a no-op.
        flight.start()
        return flight.await()
    }

    /**
     * Files a new lazily-started execution of [work] under [key] and returns it.
     *
     * **Callers must hold [guard].** This both reads and writes [inFlight], and its atomicity with
     * the lookup in [runOnce] is the whole reason two simultaneous callers share one execution.
     */
    private fun registerFlight(key: K, work: suspend () -> V): Deferred<V> {
        // The execution needs to recognise its own entry to release it, and the entry does not
        // exist until `async` has returned. Lazy start is what makes reading this safe: nothing in
        // the body can run before `runOnce` calls `start()`, which is after the assignment below.
        lateinit var flight: Deferred<V>
        flight =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    work()
                } finally {
                    // Released by the execution itself, in a `finally` so a failure releases the
                    // key as surely as a success does, and never from a completion callback — a
                    // callback runs after the execution completes, leaving a window in which the
                    // key is held by something already finished.
                    //
                    // NonCancellable because `Mutex.lock()` suspends: in an execution that is being
                    // cancelled, an ordinary lock attempt throws instead of acquiring, and the
                    // entry would be stranded in the map forever — every later caller for that key
                    // would then join a dead execution and never get an answer.
                    withContext(NonCancellable) {
                        guard.withLock {
                            // Identity-guarded rather than unconditional: a key may only be
                            // released by the execution currently filed under it, so a late release
                            // cannot drop a newer execution that has since claimed the same key.
                            // A guard rather than an assertion, because this runs on the path that
                            // delivers the result — throwing here would replace a perfectly good
                            // result with a bookkeeping failure for every waiter.
                            if (inFlight[key] === flight) {
                                inFlight.remove(key)
                            }
                        }
                    }
                }
            }
        inFlight[key] = flight
        return flight
    }
}
