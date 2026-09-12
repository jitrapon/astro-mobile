package io.jitrapon.astro.data.query

import kotlin.time.TimeSource

/**
 * A monotonic tick source, read by everything in this package that needs to know how old something
 * is.
 *
 * It exists so nothing here reads a clock it does not own. Staleness is the one thing this package
 * decides that a caller cannot otherwise steer: an entry aged against the process clock can only be
 * aged by waiting for it. Taking the clock as a dependency lets a test back it with a coroutine
 * test scheduler instead, so an entry crosses the staleness boundary the instant the test says it
 * does and nothing sleeps.
 *
 * **The origin is arbitrary — only the difference between two reads means anything.** Two ticks
 * subtracted give an elapsed duration; one tick alone gives nothing. It is not a wall-clock instant
 * and cannot be turned into one, so it is no use for anything a user would see.
 *
 * **The scale is monotonic rather than wall-clock**, and that is the point rather than an
 * implementation detail. A wall clock steps backwards when the device corrects its time and jumps
 * forwards when someone sets it, either of which would make a remembered screen look arbitrarily
 * stale or arbitrarily fresh. A monotonic scale only ever moves forward.
 */
internal fun interface Ticker {

    /**
     * Reads the current tick, in nanoseconds since this source's arbitrary origin.
     *
     * Never smaller than a tick this same instance already returned. Ticks read from two different
     * instances compare to nothing, since each carries its own origin.
     *
     * Nanoseconds in a `Long` rather than a [kotlin.time.Duration] or a [kotlin.time.TimeMark]
     * keeps the value comparable across implementations — a mark from the platform's monotonic
     * source and one from a virtual clock have no common scale and cannot be compared at all — and
     * keeps the read allocation-free on a path taken once per lookup.
     */
    fun readTickNanos(): Long
}

/**
 * The production [Ticker]: the platform's own monotonic clock, as [TimeSource.Monotonic] exposes
 * it.
 *
 * The origin is the moment the instance was constructed. One instance is bound for the whole graph
 * precisely so every read lands on one scale; a second instance would start a second origin, and a
 * tick from each would subtract to a duration that measures nothing.
 */
internal class MonotonicTicker : Ticker {

    private val origin = TimeSource.Monotonic.markNow()

    override fun readTickNanos(): Long = origin.elapsedNow().inWholeNanoseconds
}
