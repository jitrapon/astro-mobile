package io.jitrapon.astro.data.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * Pins the two properties the rest of this package leans on a [Ticker] for: that the virtual-time
 * implementation moves only when a case moves it, and that the production one only ever moves
 * forward.
 *
 * The first is what lets every age-dependent case here be written as a statement rather than a
 * wait. A regression in it would not surface as a clean failure in those cases — a helper that
 * quietly read the process clock would leave them passing for the wrong reason or not settling at
 * all, which is far harder to read back from the case than from here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TickerTest {

    @Test
    fun virtualTimeTickerMovesExactlyAsFarAsTheCaseAdvancesTheClock() = runTest {
        val virtualTime = VirtualTimeTicker(testScheduler)
        val realTime = MonotonicTicker()
        val virtualStart = virtualTime.readTickNanos()
        val realStart = realTime.readTickNanos()

        // Read twice with nothing in between: the scheduler is the only thing that may move it.
        assertEquals(virtualStart, virtualTime.readTickNanos())

        advanceTimeBy(1.hours)

        assertEquals(virtualStart + 1.hours.inWholeNanoseconds, virtualTime.readTickNanos())
        // An hour of it, at no real cost. An implementation that slept through the advance, or one
        // reading the process clock, would spend the hour here instead of stating it.
        assertTrue(
            realTime.readTickNanos() - realStart < VIRTUAL_ADVANCE_REAL_TIME_BUDGET,
            "advancing an hour of virtual time must not spend real time waiting for it",
        )
    }

    @Test
    fun monotonicTickerMovesForwardAndNeverBackwards() {
        val ticker = MonotonicTicker()
        val first = ticker.readTickNanos()
        var previous = first

        repeat(READS_PER_MONOTONICITY_SWEEP) {
            val current = ticker.readTickNanos()
            assertTrue(current >= previous, "read $current after $previous — the scale went back")
            previous = current
        }

        // Without this the sweep would also pass against a source stuck at one value, which reports
        // every entry as ageless and would leave a stale screen fresh forever.
        assertTrue(previous > first, "the scale never advanced across the sweep")
    }

    private companion object {

        /**
         * Slack for the real time a virtual hour is allowed to cost. Wide enough that a loaded
         * machine cannot trip it, narrow enough that anything actually waiting out the hour does.
         */
        val VIRTUAL_ADVANCE_REAL_TIME_BUDGET = 1.minutes.inWholeNanoseconds

        /**
         * Reads per sweep — enough that a clock of any plausible resolution has ticked at least
         * once, so the sweep shows the scale advancing rather than only failing to go backwards.
         */
        const val READS_PER_MONOTONICITY_SWEEP = 10_000
    }
}
