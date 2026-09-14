package io.jitrapon.astro.data.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * Pins what a remembered screen is allowed to survive and what removes it.
 *
 * The load-bearing case is the last one: age never evicts. Everything above this cache depends on
 * an expired entry still being readable, because that is what a refresh paints while the fresh
 * screen loads. A cache that dropped an entry on expiry would satisfy every other case here and
 * still turn every refresh into a blank skeleton.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryScreenCacheTest {

    @Test
    fun writtenScreenIsReadableAndStampedWithTheTickAndSchemaVersionItWasStoredUnder() = runTest {
        val ticker = VirtualTimeTicker(testScheduler)
        val cache = newCache(ticker)

        advanceTimeBy(1.days)
        cache.write(MONTH_KEY, "january")

        val entry = assertNotNull(cache.read(MONTH_KEY), "the written entry was not readable")
        assertEquals("january", entry.screen)
        assertEquals(ticker.readTickNanos(), entry.storedAtTick)
        assertEquals(SCHEMA_VERSION, entry.storedUnderSchemaVersion)
    }

    @Test
    fun readingAKeyNothingWasFiledUnderAnswersNull() = runTest {
        val cache = newCache()

        cache.write(MONTH_KEY, "january")

        assertNull(cache.read(AGENDA_KEY))
    }

    @Test
    fun rewritingAKeyReplacesTheScreenRatherThanAccumulating() = runTest {
        val cache = newCache()

        cache.write(MONTH_KEY, "january")
        cache.write(MONTH_KEY, "february")

        assertEquals("february", cache.read(MONTH_KEY)?.screen)
    }

    @Test
    fun evictDropsOnlyTheEntriesItsPredicateMatches() = runTest {
        val cache = newCache()
        cache.write(MONTH_KEY, "january")
        cache.write(AGENDA_KEY, "today")

        cache.evict { it.startsWith("month") }

        assertNull(cache.read(MONTH_KEY), "the matching entry survived eviction")
        assertEquals("today", cache.read(AGENDA_KEY)?.screen, "a non-matching entry was evicted")
    }

    @Test
    fun evictingOnAPredicateNothingMatchesLeavesEveryEntryInPlace() = runTest {
        val cache = newCache()
        cache.write(MONTH_KEY, "january")
        cache.write(AGENDA_KEY, "today")

        cache.evict { false }

        assertEquals("january", cache.read(MONTH_KEY)?.screen)
        assertEquals("today", cache.read(AGENDA_KEY)?.screen)
    }

    @Test
    fun writingBeyondTheCapDropsTheLeastRecentlyWrittenEntry() = runTest {
        val cache = newCache(maxEntries = 2)

        cache.write("first", "1")
        cache.write("second", "2")
        cache.write("third", "3")

        assertNull(cache.read("first"), "the oldest write survived a write past the cap")
        assertEquals("2", cache.read("second")?.screen)
        assertEquals("3", cache.read("third")?.screen)
    }

    @Test
    fun rewritingAnEntryMakesItTheNewestSoTheCapEvictsSomethingElse() = runTest {
        val cache = newCache(maxEntries = 2)

        cache.write("first", "1")
        cache.write("second", "2")
        // Refreshes "first" — after this "second" is the least recently written, even though
        // "first" was inserted before it.
        cache.write("first", "1 refreshed")
        cache.write("third", "3")

        assertEquals("1 refreshed", cache.read("first")?.screen, "a refreshed entry was evicted")
        assertNull(cache.read("second"), "the least recently written entry survived")
        assertEquals("3", cache.read("third")?.screen)
    }

    @Test
    fun readingAnEntryDoesNotReprieveItFromTheCap() = runTest {
        val cache = newCache(maxEntries = 2)
        cache.write("first", "1")
        cache.write("second", "2")

        // Reads are not writes: the cap orders entries by when each was last written, so however
        // often the oldest is read it is still the next to go.
        repeat(READS_BEFORE_OVERFLOW) { cache.read("first") }
        cache.write("third", "3")

        assertNull(cache.read("first"), "reading an entry moved it out of eviction order")
    }

    @Test
    fun anEntryPastEveryPlausibleStalenessWindowIsStillReadable() = runTest {
        val ticker = VirtualTimeTicker(testScheduler)
        val cache = newCache(ticker)
        cache.write(MONTH_KEY, "january")
        val storedAtTick = cache.read(MONTH_KEY)?.storedAtTick

        advanceTimeBy(AGE_BEYOND_ANY_WINDOW)

        val entry = assertNotNull(cache.read(MONTH_KEY), "an aged entry was evicted by age alone")
        assertEquals("january", entry.screen)
        // Still stamped with when it was written, not when it was read — the age an expiry
        // decision is made from is the entry's own, and re-stamping on read would make every
        // entry permanently fresh.
        assertEquals(storedAtTick, entry.storedAtTick)
    }

    @Test
    fun aCacheThatCouldHoldNothingIsRejectedAtConstruction() {
        // A zero cap would evict every entry inside the write that stored it, so every read misses
        // and every observation refetches — a silently disabled cache rather than a small one.
        assertFailsWith<IllegalArgumentException> { newCache(maxEntries = 0) }
    }

    private fun newCache(
        ticker: Ticker = Ticker { 0L },
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): InMemoryScreenCache<String, String> =
        InMemoryScreenCache(
            ticker = ticker,
            storedUnderSchemaVersion = SCHEMA_VERSION,
            maxEntries = maxEntries,
        )

    private companion object {
        const val MONTH_KEY = "month:2026-09"
        const val AGENDA_KEY = "agenda:2026-09-12"
        const val SCHEMA_VERSION = "0.2.0"
        const val DEFAULT_MAX_ENTRIES = 8
        const val READS_BEFORE_OVERFLOW = 5

        /** Far past any staleness window a screen cache would plausibly be configured with. */
        val AGE_BEYOND_ANY_WINDOW = 365.days
    }
}
