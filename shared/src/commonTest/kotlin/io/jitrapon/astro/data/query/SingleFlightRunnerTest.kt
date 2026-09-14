package io.jitrapon.astro.data.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins which callers share an execution, which get their own, and — the case the others cannot
 * distinguish — *what* releases a key.
 *
 * The load-bearing pair is the last two. A runner that releases the key when an awaiting caller
 * exits, rather than when the execution it is waiting on completes, satisfies every other case here
 * and still starts a duplicate exchange for the next caller: the abandoned execution keeps running
 * unobserved while a second one is spun up beside it. Only counting invocations and asserting the
 * later caller receives the *original* execution's result tells the two implementations apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightRunnerTest {

    @Test
    fun twoCallersOfOneKeyRunTheWorkOnceAndBothReceiveThatOneResult() = runTest {
        val responder = GatedResponder()
        val runner = SingleFlightRunner<String, String>(backgroundScope)

        val first = async { runner.runOnce(MONTH_KEY) { responder.respond() } }
        val second = async { runner.runOnce(MONTH_KEY) { responder.respond() } }
        // Both callers reach the runner before anything can complete, which is the arrival order
        // deduplication has to handle — a second caller landing after the first has finished would
        // legitimately start its own execution and prove nothing.
        runCurrent()
        responder.releaseAll()

        assertEquals("response 1", first.await())
        assertEquals("response 1", second.await(), "the second caller received its own execution")
        assertEquals(1, responder.invocations, "the work ran more than once for one key")
    }

    @Test
    fun callersOfDifferentKeysEachRunTheirOwnWork() = runTest {
        val responder = GatedResponder()
        val runner = SingleFlightRunner<String, String>(backgroundScope)

        val month = async { runner.runOnce(MONTH_KEY) { responder.respond() } }
        val agenda = async { runner.runOnce(AGENDA_KEY) { responder.respond() } }
        runCurrent()
        responder.releaseAll()

        assertEquals("response 1", month.await())
        assertEquals("response 2", agenda.await(), "two keys were collapsed onto one execution")
        assertEquals(2, responder.invocations)
    }

    @Test
    fun cancellingOneCallerLeavesTheSharedExecutionRunningForTheOthers() = runTest {
        val responder = GatedResponder()
        val runner = SingleFlightRunner<String, String>(backgroundScope)

        val abandoning = launch { runner.runOnce(MONTH_KEY) { responder.respond() } }
        val waiting = async { runner.runOnce(MONTH_KEY) { responder.respond() } }
        runCurrent()

        abandoning.cancel()
        responder.releaseAll()

        assertEquals("response 1", waiting.await(), "a caller's exit cancelled a shared execution")
        assertEquals(1, responder.invocations)
    }

    @Test
    fun anExecutionThatCompletesAfterItsOnlyCallerLeftStillReleasesItsKey() = runTest {
        val responder = GatedResponder()
        val runner = SingleFlightRunner<String, String>(backgroundScope)

        val abandoning = launch { runner.runOnce(MONTH_KEY) { responder.respond() } }
        runCurrent()
        abandoning.cancel()
        responder.releaseAll()
        // `runCurrent` rather than `advanceUntilIdle`: the abandoned execution belongs to the
        // runner's own scope, and `advanceUntilIdle` stops once the test's own coroutines are idle
        // without necessarily driving background work to completion — which would leave the
        // execution suspended and this case asserting nothing.
        runCurrent()

        // A key stranded by an execution completing with nobody waiting is permanently poisoned:
        // every later caller joins something already finished, so this call would answer with the
        // abandoned execution's result rather than running at all.
        val afterRelease = runner.runOnce(MONTH_KEY) { responder.respond() }

        assertEquals("response 2", afterRelease, "the key was still held by a finished execution")
        assertEquals(2, responder.invocations)
    }

    @Test
    fun aCallerArrivingAfterTheOnlyOtherLeftJoinsTheRunningExecutionRatherThanStartingASecond() =
        runTest {
            val responder = GatedResponder()
            val runner = SingleFlightRunner<String, String>(backgroundScope)

            val abandoning = launch { runner.runOnce(MONTH_KEY) { responder.respond() } }
            runCurrent()
            abandoning.cancel()
            // The execution is still running, unobserved. A runner that released the key on the
            // caller's exit would let this call start a second one — and would still resolve, which
            // is why the assertions below are about the invocation count and whose result arrives,
            // not about whether anything came back.
            val joining = async { runner.runOnce(MONTH_KEY) { responder.respond() } }
            runCurrent()
            responder.releaseAll()

            assertEquals(
                "response 1",
                joining.await(),
                "the later caller got its own execution instead of joining the running one",
            )
            assertEquals(1, responder.invocations, "the abandoned execution was duplicated")
        }

    /**
     * Work that records how many times it was invoked and does not finish until told to, so a case
     * can hold an execution open across a cancellation and still name which invocation's result
     * came back.
     *
     * Each invocation's result identifies the invocation, which is what lets a case assert that a
     * caller received *the original* execution's result rather than merely receiving something.
     */
    private class GatedResponder {

        var invocations = 0
            private set

        private val gate = CompletableDeferred<Unit>()

        suspend fun respond(): String {
            val invocation = ++invocations
            gate.await()
            return "response $invocation"
        }

        /** Lets every held invocation finish, and every later one finish without suspending. */
        fun releaseAll() {
            gate.complete(Unit)
        }
    }

    private companion object {
        const val MONTH_KEY = "month:2026-09"
        const val AGENDA_KEY = "agenda:2026-09-12"
    }
}
