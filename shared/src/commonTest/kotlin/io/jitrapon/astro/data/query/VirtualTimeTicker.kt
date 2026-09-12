package io.jitrapon.astro.data.query

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * A [Ticker] reading a coroutine test scheduler's virtual clock, so age advances exactly when a
 * case says it does and never otherwise.
 *
 * `advanceTimeBy` moves this ticker; real time passing moves it not at all. A case needing an entry
 * to cross a staleness boundary therefore states the crossing rather than sleeping through it, and
 * costs the microseconds a virtual clock takes instead of the minutes a real one would — which is
 * what makes staleness testable on both the JVM host and the simulator at all.
 *
 * The scheduler counts milliseconds and a [Ticker] answers in nanoseconds, so the conversion lives
 * here once rather than at every read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualTimeTicker(private val scheduler: TestCoroutineScheduler) : Ticker {

    override fun readTickNanos(): Long = scheduler.currentTime * NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
