package io.jitrapon.astro.data.query

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One remembered screen, paired with the two facts that decide whether it may still be shown.
 *
 * Neither fact is derivable from [screen] itself, and both are stamped by the cache rather than
 * supplied by the writer — a caller that could name its own [storedAtTick] could make an entry
 * arbitrarily fresh, which is the one thing staleness must not be able to be lied about.
 *
 * The entry carries the facts and decides nothing: whether they add up to a usable screen is
 * [ScreenCachePolicy]'s answer, not this type's.
 */
internal data class CachedScreen<S : Any>(
    val screen: S,
    /**
     * The [Ticker] reading taken when this entry was written. Only its difference from a later
     * reading means anything, and only against the same ticker instance.
     */
    val storedAtTick: Long,
    /**
     * The response-schema version the writing build understood.
     *
     * An entry written by a build that spoke an older contract describes a screen these models were
     * never written against, and deserializing it would produce a structurally valid screen with
     * defaults standing in for everything the contract has since changed — the same failure the
     * exact-version check on the wire exists to prevent, arriving through storage instead.
     */
    val storedUnderSchemaVersion: String,
)

/**
 * Remembers the most recent screen for a request so a later observation of the same request can
 * paint immediately instead of waiting out a round trip.
 *
 * **Age alone never evicts.** An entry past its staleness window stays readable precisely so it can
 * be shown while a fresh one loads; dropping it would collapse a refresh into a blank skeleton,
 * which is the state this cache exists to avoid. Only capacity pressure and [evict] remove
 * anything.
 *
 * The operations suspend because the in-memory implementation is not the only one intended: the
 * schema-version stamp on [CachedScreen] is inert while entries die with the process and becomes
 * load-bearing the moment they outlive it, and a disk-backed implementation is suspending I/O. A
 * blocking signature here would have to be widened later through every caller.
 *
 * @param K the request identity an entry is filed under. Two requests that differ in any way ask
 *   for different screens, so an implementation must key on the whole value and never on part of
 *   it.
 * @param S the remembered screen.
 */
internal interface ScreenCache<K : Any, S : Any> {

    /** Returns the entry filed under [key], or `null` if nothing is filed there. */
    suspend fun read(key: K): CachedScreen<S>?

    /**
     * Files [screen] under [key], replacing whatever was there and stamping the entry with the
     * current tick and the schema version this build stores under.
     */
    suspend fun write(key: K, screen: S)

    /**
     * Drops every entry whose key satisfies [matches], and leaves the rest untouched.
     *
     * A predicate rather than a key so a caller can drop a whole family at once — every entry for a
     * view, a time zone, or a date range — without first enumerating which keys exist, which it has
     * no way to know.
     */
    suspend fun evict(matches: (K) -> Boolean)
}

/**
 * A [ScreenCache] holding entries in memory, bounded to [maxEntries] and dropping the
 * least-recently-written entry when a write would exceed that.
 *
 * **Least-recently-*written*, not least-recently-read**: reads do not reprieve an entry, and
 * rewriting an existing key moves it back to the newest position. Insertion order alone would not
 * give the second of those — replacing a value in a [LinkedHashMap] leaves the key where it first
 * landed — so a key refreshed on every exchange would be evicted ahead of one written once and
 * never touched again.
 *
 * All state is reached under one [Mutex]. This is not a throughput concern but a correctness one on
 * Kotlin/Native, where a map mutated from two threads corrupts rather than throwing: the failure
 * surfaces as a process-level crash far from the write that caused it, not as an exception any
 * caller could catch.
 *
 * @param storedUnderSchemaVersion the response-schema version this build understands, stamped onto
 *   every entry written.
 */
internal class InMemoryScreenCache<K : Any, S : Any>(
    private val ticker: Ticker,
    private val storedUnderSchemaVersion: String,
    private val maxEntries: Int,
) : ScreenCache<K, S> {

    init {
        require(maxEntries > 0) {
            "A cache must hold at least one entry; maxEntries was $maxEntries."
        }
    }

    private val guard = Mutex()

    /** Ordered oldest-written first, which is the order [write] evicts in. */
    private val entries = LinkedHashMap<K, CachedScreen<S>>()

    override suspend fun read(key: K): CachedScreen<S>? = guard.withLock { entries[key] }

    override suspend fun write(key: K, screen: S) {
        val entry =
            CachedScreen(
                screen = screen,
                storedAtTick = ticker.readTickNanos(),
                storedUnderSchemaVersion = storedUnderSchemaVersion,
            )
        guard.withLock {
            // Remove before putting so a rewritten key is re-filed as the newest rather than
            // keeping the position of its first write.
            entries.remove(key)
            entries[key] = entry
            // One write adds at most one entry to a map that was already within the cap, so the
            // map can only ever be one over it — a loop here would iterate once and read as
            // though the overflow were unbounded.
            if (entries.size > maxEntries) {
                entries.remove(entries.keys.first())
            }
        }
    }

    override suspend fun evict(matches: (K) -> Boolean) {
        guard.withLock {
            // Collected before removing: mutating the map through the key view while iterating it
            // is a catchable exception on the JVM and memory corruption on Kotlin/Native.
            entries.keys.filter(matches).forEach(entries::remove)
        }
    }
}
