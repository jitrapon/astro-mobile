package io.jitrapon.astro

import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every state one collector received, in order, alongside the handle that stops it collecting.
 *
 * Order is what most cases over an observation assert on: "painted the remembered screen, then the
 * fresh one" is a claim about a sequence, and the final state alone cannot tell it apart from
 * having painted nothing until the exchange landed.
 *
 * The recording is itself a [StateFlow] so a case can *wait* for a state rather than pump a
 * scheduler for it. That is not a convenience: an exchange travels through Ktor's engine on a real
 * dispatcher, so draining the test scheduler says nothing about whether the backend has answered,
 * and a case that asserted right afterwards would be asserting on a half-finished exchange.
 */
internal class StateRecording<T>(
    private val recorded: StateFlow<List<T>>,
    private val collector: Job,
) {

    /** Every state delivered so far, none of them lost to conflation. */
    val states: List<T>
        get() = recorded.value

    /** The most recent state, failing the test when nothing has been published at all. */
    val latest: T
        get() = recorded.value.lastOrNull() ?: fail("The observation published nothing.")

    /**
     * Suspends until the most recent state satisfies [predicate], and returns it.
     *
     * Deliberately carries no deadline of its own: `runTest` already fails a test whose body stops
     * making progress, while a deadline expressed in virtual time would fire on a test scheduler
     * that is merely idle because a real dispatcher is doing the work.
     */
    suspend fun awaitLatest(predicate: (T) -> Boolean): T =
        recorded.mapNotNull { it.lastOrNull() }.first(predicate)

    /** Ends the collection, the way a caller's scope ending would. */
    fun stopCollecting() {
        collector.cancel()
    }
}

/**
 * Collects [states] into a [StateRecording] on this scope.
 *
 * Collected on a scope that outlives the test body — `backgroundScope` — because an observation
 * never completes: collecting it from the test's own job would leave the test waiting forever for a
 * flow that is designed never to end.
 */
internal fun <T> CoroutineScope.recordStates(states: Flow<T>): StateRecording<T> {
    val recorded = MutableStateFlow<List<T>>(emptyList())
    val collector = launch { states.collect { state -> recorded.update { it + state } } }
    return StateRecording(recorded, collector)
}
