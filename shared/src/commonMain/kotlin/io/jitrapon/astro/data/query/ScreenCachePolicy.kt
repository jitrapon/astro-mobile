package io.jitrapon.astro.data.query

import kotlin.time.Duration

/**
 * What may be done with a remembered screen: shown as-is, shown while a fresh one loads, or not
 * shown at all.
 *
 * Three values rather than a boolean, because "may I show this?" and "must I refetch?" are separate
 * questions and a two-valued answer forces them together. Collapsing [STALE] into [UNUSABLE] turns
 * every refresh into a blank skeleton; collapsing it into [FRESH] leaves a screen painting old
 * content with nothing on its way to replace it.
 */
internal enum class ScreenCacheVerdict {

    /** Show it and exchange nothing. The entry is recent enough to stand on its own. */
    FRESH,

    /**
     * Show it *and* exchange. Past its window, so it is no longer trusted as the answer — but it is
     * still the best thing to paint until the answer arrives, which is the whole reason the cache
     * keeps aged entries readable.
     */
    STALE,

    /**
     * Show nothing and exchange. The entry describes a screen this build cannot read, so painting
     * it would be worse than painting nothing.
     */
    UNUSABLE,
}

/**
 * Decides what a caller may do with a remembered screen.
 *
 * Separate from [ScreenCache] because remembering and judging are different jobs with different
 * reasons to change: the cache's rules are about capacity and identity, the policy's are about age
 * and contract. Keeping the judgement out of the cache is also what allows the cache's central
 * guarantee — that age alone never evicts — to hold at all; a cache that knew the staleness window
 * would inevitably enforce it.
 */
internal fun interface ScreenCachePolicy {

    /**
     * Rates [entry] as of [nowTick], a reading from the same [Ticker] that stamped it.
     *
     * Passing the reading in rather than taking a [Ticker] here keeps one decision reading one
     * clock once: a caller that must ask about several entries in a single pass would otherwise
     * rate them against slightly different instants, so two entries written together could come
     * back rated differently.
     *
     * The entry is [CachedScreen] with its screen type unconstrained because the verdict is decided
     * from the stamps alone — a type parameter would advertise an inspection of the screen that no
     * implementation performs.
     */
    fun classifyCachedScreen(entry: CachedScreen<*>, nowTick: Long): ScreenCacheVerdict
}

/**
 * The default [ScreenCachePolicy]: an entry is [ScreenCacheVerdict.FRESH] until it has been held
 * for [stalenessWindow], [ScreenCacheVerdict.STALE] after that, and [ScreenCacheVerdict.UNUSABLE]
 * at any age if it was stored under a schema version other than [supportedSchemaVersion].
 *
 * **The schema check is asked first and is not overridable by age.** An entry written under a
 * contract this build was not compiled against describes fields these models no longer have and
 * carries defaults for the ones they gained; it is wrong on arrival rather than wrong eventually,
 * so no amount of freshness may reprieve it.
 *
 * **The [ScreenCacheVerdict.UNUSABLE] branch is inert while the cache lives in memory** — entries
 * die with the process, so nothing can outlive the build that wrote it and every entry is stamped
 * with the version this build supports. It becomes load-bearing the moment entries survive a
 * restart: a disk-backed cache carries screens written by the previous install straight across an
 * app update, which is exactly the case the exact-version check on the wire already guards against
 * and which would otherwise arrive through storage unguarded.
 *
 * @param supportedSchemaVersion the response-schema version this build understands. Same value the
 *   cache stamps entries with, supplied to both from one place so the two cannot disagree.
 * @param stalenessWindow how long a written entry stands as the answer. Measured from when the
 *   entry was written, not from when it was last read — reading does not make a screen newer.
 */
internal class StalenessWindowScreenCachePolicy(
    private val supportedSchemaVersion: String,
    stalenessWindow: Duration,
) : ScreenCachePolicy {

    init {
        require(stalenessWindow.isPositive()) {
            "A staleness window must be positive; an entry rated stale the instant it was written " +
                "would make every observation exchange. The window was $stalenessWindow."
        }
    }

    private val stalenessWindowNanos: Long = stalenessWindow.inWholeNanoseconds

    override fun classifyCachedScreen(
        entry: CachedScreen<*>,
        nowTick: Long,
    ): ScreenCacheVerdict =
        when {
            entry.storedUnderSchemaVersion != supportedSchemaVersion -> ScreenCacheVerdict.UNUSABLE
            // Held for exactly the window is already past it: the window is how long the entry
            // stands, so the reading at which it has stood that long is the first at which it no
            // longer does.
            nowTick - entry.storedAtTick < stalenessWindowNanos -> ScreenCacheVerdict.FRESH
            else -> ScreenCacheVerdict.STALE
        }
}
