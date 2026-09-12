package io.jitrapon.astro.data.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * Pins the three verdicts and, more importantly, what each one may not be confused with.
 *
 * Two cases carry the weight. The boundary is crossed under the virtual clock from both sides at
 * one instant — an entry written before it rates stale while one written after rates fresh — which
 * is what proves the verdict follows the entry's own age rather than the reading it was asked
 * about. And an entry stored under the wrong schema version is unusable at every age, including
 * ages at which a supported entry would still be fresh, so freshness cannot reprieve a screen this
 * build cannot read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StalenessWindowScreenCachePolicyTest {

    @Test
    fun entryHeldForLessThanTheWindowIsFresh() = runTest {
        val ticker = VirtualTimeTicker(testScheduler)
        val policy = newPolicy()
        val entry = entryStoredAt(ticker)

        advanceTimeBy(WINDOW / 2)

        assertEquals(
            ScreenCacheVerdict.FRESH,
            policy.classifyCachedScreen(entry, ticker.readTickNanos()),
        )
    }

    @Test
    fun entryHeldForExactlyTheWindowIsAlreadyStale() {
        val policy = newPolicy()
        val entry = entryStoredAt(storedAtTick = 0L)

        // The window is how long the entry stands as the answer, so the reading at which it has
        // stood that long is the first at which it no longer does.
        assertEquals(
            ScreenCacheVerdict.STALE,
            policy.classifyCachedScreen(entry, WINDOW.inWholeNanoseconds),
        )
        assertEquals(
            ScreenCacheVerdict.FRESH,
            policy.classifyCachedScreen(entry, WINDOW.inWholeNanoseconds - 1),
            "the last tick inside the window was rated stale",
        )
    }

    @Test
    fun entryHeldPastTheWindowIsStaleRatherThanUnusable() = runTest {
        val ticker = VirtualTimeTicker(testScheduler)
        val policy = newPolicy()
        val entry = entryStoredAt(ticker)

        advanceTimeBy(AGE_BEYOND_ANY_WINDOW)

        // However old it gets it stays showable — this is the verdict a refresh paints while the
        // fresh screen loads, and rating it unusable would collapse that into a blank skeleton.
        assertEquals(
            ScreenCacheVerdict.STALE,
            policy.classifyCachedScreen(entry, ticker.readTickNanos()),
        )
    }

    @Test
    fun oneReadingRatesAnOlderEntryStaleAndANewerEntryFresh() = runTest {
        val ticker = VirtualTimeTicker(testScheduler)
        val policy = newPolicy()
        val written = entryStoredAt(ticker)

        advanceTimeBy(WINDOW * 2)
        val rewritten = entryStoredAt(ticker)
        val nowTick = ticker.readTickNanos()

        assertEquals(ScreenCacheVerdict.STALE, policy.classifyCachedScreen(written, nowTick))
        assertEquals(
            ScreenCacheVerdict.FRESH,
            policy.classifyCachedScreen(rewritten, nowTick),
            "rewriting an entry did not restore its freshness",
        )
    }

    @Test
    fun entryStoredUnderAnUnsupportedSchemaVersionIsUnusableWhileStillInsideTheWindow() = runTest {
        val ticker = VirtualTimeTicker(testScheduler)
        val policy = newPolicy()
        val entry = entryStoredAt(ticker, schemaVersion = UNSUPPORTED_SCHEMA_VERSION)

        advanceTimeBy(WINDOW / 2)

        // An age at which a supported entry would still be fresh: the contract mismatch is not
        // something freshness is allowed to outvote.
        assertEquals(
            ScreenCacheVerdict.UNUSABLE,
            policy.classifyCachedScreen(entry, ticker.readTickNanos()),
        )
    }

    @Test
    fun entryStoredUnderAnUnsupportedSchemaVersionIsUnusableRatherThanStalePastTheWindow() =
        runTest {
            val ticker = VirtualTimeTicker(testScheduler)
            val policy = newPolicy()
            val entry = entryStoredAt(ticker, schemaVersion = UNSUPPORTED_SCHEMA_VERSION)

            advanceTimeBy(AGE_BEYOND_ANY_WINDOW)

            // Stale would let it paint while a fresh screen loads; this one may not be painted at
            // all, so age must not soften the verdict.
            assertEquals(
                ScreenCacheVerdict.UNUSABLE,
                policy.classifyCachedScreen(entry, ticker.readTickNanos()),
            )
        }

    @Test
    fun aVersionBelowTheSupportedOneIsAsUnusableAsOneAbove() {
        val policy = newPolicy()

        assertEquals(
            ScreenCacheVerdict.UNUSABLE,
            policy.classifyCachedScreen(entryStoredAt(0L, schemaVersion = "0.1.0"), 0L),
        )
        assertEquals(
            ScreenCacheVerdict.UNUSABLE,
            policy.classifyCachedScreen(entryStoredAt(0L, schemaVersion = "0.3.0"), 0L),
        )
    }

    @Test
    fun aWindowThatCouldNeverRateAnythingFreshIsRejectedAtConstruction() {
        // A zero or negative window rates every entry stale the instant it is written, so every
        // observation exchanges — freshness silently disabled rather than configured short.
        assertFailsWith<IllegalArgumentException> { newPolicy(window = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { newPolicy(window = -WINDOW) }
    }

    private fun newPolicy(window: Duration = WINDOW): ScreenCachePolicy =
        StalenessWindowScreenCachePolicy(
            supportedSchemaVersion = SCHEMA_VERSION,
            stalenessWindow = window,
        )

    private fun entryStoredAt(
        ticker: Ticker,
        schemaVersion: String = SCHEMA_VERSION,
    ): CachedScreen<String> = entryStoredAt(ticker.readTickNanos(), schemaVersion)

    private fun entryStoredAt(
        storedAtTick: Long,
        schemaVersion: String = SCHEMA_VERSION,
    ): CachedScreen<String> =
        CachedScreen(
            screen = "january",
            storedAtTick = storedAtTick,
            storedUnderSchemaVersion = schemaVersion,
        )

    private companion object {
        const val SCHEMA_VERSION = "0.2.0"
        const val UNSUPPORTED_SCHEMA_VERSION = "0.1.0"
        val WINDOW = 5.minutes

        /** Far past any staleness window a screen cache would plausibly be configured with. */
        val AGE_BEYOND_ANY_WINDOW = 365.days
    }
}
