package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.Result
import io.jitrapon.astro.data.query.ScreenCache
import io.jitrapon.astro.data.query.ScreenCachePolicy
import io.jitrapon.astro.data.query.ScreenCacheVerdict
import io.jitrapon.astro.data.query.SingleFlightRunner
import io.jitrapon.astro.data.query.Ticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serves one calendar screen per request, over time: it publishes what is already remembered,
 * exchanges for a newer one when what it has is no longer good enough, and keeps every observer of
 * the same request looking at the same answer.
 *
 * This is the layer that turns a one-shot fetch into a subscription. What it adds beyond
 * [CalendarScreenApi] is entirely about *time and multiplicity* — which screen may be shown while a
 * newer one loads, how many exchanges several simultaneous observers cause, and what a failure does
 * to a screen that had already loaded. The exchange itself is still the API client's.
 *
 * **An observation never completes.** [observeScreen] hands back a subscription to one request's
 * state; only the collector's own scope ends it.
 *
 * **Nothing here is cancelled by an observer leaving.** An exchange runs on [scope] — the layer's
 * own — so a collector that goes away abandons its wait and nothing else: the response still lands,
 * is still remembered, and is still published for whoever observes that request next. This is what
 * makes the cancellation contract hold without a single catch block. A cancelled collector receives
 * nothing further and never a failure, because the failure branch is only ever reached by a
 * *completed* exchange that reported one.
 *
 * The published shape — a case that says what there is to paint, alongside an independent flag that
 * says whether the network is busy — follows Store5's read response, studied as a reference
 * implementation. Nothing of it is vendored.
 *
 * @param scope the scope exchanges are hosted on. A `SupervisorJob` is expected, so one request's
 *   failed exchange does not tear down another's. Cancelling it is what stops this layer.
 */
internal class CalendarScreenQuery(
    private val calendarScreenApi: CalendarScreenApi,
    private val screenCache: ScreenCache<CalendarScreenRequest, CalendarScreenResponse>,
    private val screenCachePolicy: ScreenCachePolicy,
    private val singleFlightRunner:
        SingleFlightRunner<CalendarScreenExchangeKey, Result<CalendarScreenResponse>>,
    private val ticker: Ticker,
    private val scope: CoroutineScope,
) {

    /**
     * Guards [observations] and every generation inside it, across find-or-create so two observers
     * arriving together cannot each conclude they are the first and walk away holding two different
     * states for one request, and across the generation reads and writes so an exchange and an
     * invalidation cannot disagree about which era the exchange belongs to, and across every
     * publication — an exchange's answer and a remembered screen alike — so an invalidation cannot
     * land between deciding what may be shown and showing it.
     *
     * One mutex over one table, held only for bookkeeping and never across an exchange. A
     * reader/writer split would buy nothing: every section under it is a few map operations, while
     * holding it across a round trip would serialise the whole layer onto one request at a time.
     */
    private val guard = Mutex()

    /**
     * The live state of every request observed so far, one entry per request identity.
     *
     * Entries are kept rather than dropped when their last collector leaves. Dropping one would
     * have to be decided from a subscriber count that falls to zero *as* a new observer is
     * arriving, and losing that race hands the new observer a state nothing updates any more — a
     * screen that silently never refreshes, which is a far worse failure than the memory an idle
     * entry holds. Retention is bounded by how many distinct requests a session observes, and the
     * screen an idle entry references is the same one the cache already holds until capacity
     * pressure drops it.
     */
    private val observations = mutableMapOf<CalendarScreenRequest, ObservedScreen>()

    /**
     * Observes the screen matching [request]: the current state immediately, then every state that
     * replaces it, for as long as the collector collects.
     *
     * Collecting is what starts the work. Each collection publishes whatever is remembered for
     * [request] and exchanges for a newer screen unless the remembered one is still fresh — so
     * re-observing a request is how a caller asks for it to be brought up to date, and observing it
     * twice inside the staleness window costs no exchange at all.
     *
     * Two collectors of one request share one state and cause one exchange; collectors of requests
     * that differ in any field share nothing, because the request is the screen's whole identity.
     */
    fun observeScreen(request: CalendarScreenRequest): Flow<CalendarScreenQueryState> = flow {
        val observed = guard.withLock { observations.getOrPut(request, ::ObservedScreen) }
        // The load is started from `onSubscription` rather than before `emitAll` so that it cannot
        // begin until this collector is registered with the state. Started any earlier, a load that
        // reached its first publication before registration would have that state conflated away,
        // and a collector would silently miss the transition it was attached to see.
        emitAll(
            observed.states.onSubscription { scope.launch { serveAndRefresh(request, observed) } }
        )
    }

    /**
     * Exchanges for a newer screen matching [request] and publishes what comes back, whether or not
     * the remembered screen still stands.
     *
     * This is the pull-to-refresh path, and the only difference from what a fresh observation does
     * is that it consults no staleness window: a caller asking explicitly for new data is not
     * answered with the copy it is already looking at. What it keeps is everything else — the
     * screen already showing stays showing under a raised in-flight flag, and a concurrent refresh
     * of the same request joins the one exchange rather than starting a second.
     *
     * Suspends until the exchange it joined has been dealt with, so a caller can sequence work
     * behind a completed refresh. It reports nothing: the outcome reaches callers through the
     * observed state, which is the one place a refresh's result is ever read from.
     *
     * The refresh runs on [scope] and the caller only waits for it, so cancelling the caller
     * abandons the wait and nothing else. Run in the caller's own coroutine instead, a caller
     * cancelled after raising the in-flight flag but before its exchange had started would leave
     * every other observer of [request] under a flag no exchange was ever going to lower.
     */
    suspend fun refetchScreen(request: CalendarScreenRequest) {
        scope
            .launch {
                val observed = guard.withLock { observations.getOrPut(request, ::ObservedScreen) }
                observed.markFetching()
                exchangeAndPublish(request, observed)
            }
            .join()
    }

    /**
     * Declares every remembered screen whose request satisfies [matches] to be wrong, and brings
     * whoever is watching one back up to date.
     *
     * Three things happen, and dropping any one of them leaves a screen that is known to be wrong
     * still on display:
     * - the matching entries are dropped, so the next observation cannot be served one;
     * - every matching request moves to a new generation, which is what stops an exchange that was
     *   already in flight from writing its now-obsolete answer back in behind this call — see
     *   [CalendarScreenExchangeKey];
     * - every matching request that someone is *currently collecting* is refreshed, because
     *   eviction alone would leave an already-open screen painting stale content until its
     *   collector happened to re-observe it, which for a screen left open is never.
     *
     * Only requests with a live collector are refreshed. Observations are retained for the life of
     * this layer, so refreshing them all would turn one invalidation into a round trip per screen
     * the session has ever opened. A collector that arrives *during* this call is not missed: it
     * cannot be served the entry this call has already dropped, so its own load exchanges against
     * the generation this call installed.
     *
     * A predicate rather than a request so a caller can invalidate a family — every screen for a
     * calendar, a time zone, a date range — without knowing which requests were ever observed.
     */
    suspend fun invalidateScreens(matches: (CalendarScreenRequest) -> Boolean) {
        val refreshing = guard.withLock {
            screenCache.evict(matches)
            // Collected before touching anything: the generation bump must cover every matching
            // request, while only the ones being collected are worth a round trip.
            val invalidated = observations.filterKeys(matches)
            invalidated.values.forEach { it.startNewGeneration() }
            invalidated.filterValues { it.isBeingCollected }.toList()
        }
        // Launched outside the critical section so an exchange can never be started while the table
        // that decides whether its answer may publish is locked.
        refreshing.forEach { (request, observed) ->
            scope.launch { serveAndRefresh(request, observed) }
        }
    }

    /**
     * Publishes what is remembered for [request] and exchanges for a newer screen unless what is
     * remembered is still fresh.
     *
     * The remembered screen is published *before* the exchange rather than after it, which is the
     * whole point of keeping aged entries readable: a refresh paints the last screen and a progress
     * indicator together instead of a skeleton the caller has already seen past.
     *
     * A remembered screen the policy rates unusable is not published, but neither does its absence
     * blank whatever is already showing — the in-flight flag is raised over the current state and
     * nothing else, because an entry this build cannot read says nothing about the screen a
     * previous exchange already delivered.
     *
     * Reading the remembered screen and publishing it happen in one critical section, the one
     * invalidation takes across its eviction. Read outside it, a snapshot taken just before an
     * invalidation could be published after that invalidation's refresh had already delivered the
     * replacement — and, having been rated fresh, would start no exchange to correct itself.
     */
    private suspend fun serveAndRefresh(request: CalendarScreenRequest, observed: ObservedScreen) {
        val exchangeNeeded = guard.withLock {
            val remembered = screenCache.read(request)
            val verdict = remembered?.let {
                screenCachePolicy.classifyCachedScreen(it, ticker.readTickNanos())
            }
            val showable = remembered?.takeIf { verdict != ScreenCacheVerdict.UNUSABLE }
            if (showable != null) {
                observed.publishRemembered(
                    showable.screen,
                    isFetching = verdict != ScreenCacheVerdict.FRESH,
                )
            } else {
                // Nothing showable means nothing was remembered, or what was is unreadable to this
                // build — either way an exchange is coming, so the flag goes up over whatever is
                // already there rather than replacing it.
                observed.markFetching()
            }
            verdict != ScreenCacheVerdict.FRESH
        }
        if (exchangeNeeded) exchangeAndPublish(request, observed)
    }

    /**
     * Joins the one exchange for [request] in its current generation — starting it through
     * [singleFlightRunner] if none is running — and returns once that exchange has published what
     * it produced, or been ruled out by an invalidation.
     *
     * Concurrent observers of one request reach this together and share the single execution the
     * runner starts, so they cause one round trip between them. None of them publishes: the
     * exchange does, once, in [fetchRememberAndPublish].
     */
    private suspend fun exchangeAndPublish(
        request: CalendarScreenRequest,
        observed: ObservedScreen,
    ) {
        val key = guard.withLock { observed.currentExchangeKey(request) }
        singleFlightRunner.runOnce(key) { fetchRememberAndPublish(key, observed) }
    }

    /**
     * Fetches the screen matching [key], then remembers and publishes a delivered one — or
     * publishes the failure — unless an invalidation has ruled that answer out while it was in
     * flight.
     *
     * Publication lives here, inside the work one exchange runs, rather than with the callers
     * waiting on it. The runner releases a key only after this work returns, so an exchange's
     * answer is published before a later exchange under the same key can even start. Published by
     * each waiter instead, a waiter resumed late could publish after a later refresh — one in the
     * same generation, which the generation check cannot tell apart — and put an older screen back
     * on display while the cache holds the newer one. The same placement is why a screen is
     * remembered exactly once per round trip, stamped with the tick at which it actually arrived.
     *
     * The cache write does not live in [CalendarScreenApi] either: that client's job ends at
     * turning an exchange into a [Result], and a client that wrote to a cache could not be used for
     * an exchange whose answer should not be remembered.
     *
     * The generation check, the write and the publication happen in one critical section, and
     * invalidation takes the same lock across its own eviction and bump. Checking outside the lock
     * would let a bump land between a passing check and the write or publication, re-filing or
     * re-displaying a screen the server has already said is wrong.
     *
     * A failure publishes [CalendarScreenQueryState.Failed] carrying whatever was showing when it
     * landed, so a failed refresh reports itself without discarding a screen that loaded perfectly
     * well. Cancellation reaches neither branch: it propagates out of the API client untouched
     * rather than arriving as an error, so a cancelled exchange publishes nothing at all.
     *
     * An answer from a superseded generation is discarded rather than published, and the in-flight
     * flag is deliberately left raised when that happens: the refresh the invalidation started is
     * still running, and lowering the flag on its behalf would report the screen as settled while a
     * newer answer is still on its way. That refresh is what lowers it — an invalidation with a
     * live collector always starts one, and a request with no live collector reaches its next
     * collector through [serveAndRefresh], which sets the flag from what it finds rather than from
     * what was left behind.
     *
     * @param observed the state of [key]'s request. Whichever caller starts the exchange supplies
     *   it, and every joining caller holds the same instance, since [observations] never replaces
     *   an entry.
     */
    private suspend fun fetchRememberAndPublish(
        key: CalendarScreenExchangeKey,
        observed: ObservedScreen,
    ): Result<CalendarScreenResponse> {
        val outcome = calendarScreenApi.fetchCalendarScreen(key.request)
        guard.withLock {
            if (!observed.isCurrent(key)) return outcome
            when (outcome) {
                is Result.Success -> {
                    screenCache.write(key.request, outcome.data)
                    observed.publishDelivered(outcome.data)
                }
                is Result.Error -> observed.publishFailure(outcome.exception)
            }
        }
        return outcome
    }
}

/**
 * What one exchange for a calendar screen is deduplicated under: the request being exchanged for,
 * and the generation of that request it was started in.
 *
 * The generation is in the key rather than checked alongside it because the two invariants an
 * invalidation has to establish are otherwise separate problems. Carrying it here settles both with
 * one comparison:
 * - a caller arriving after an invalidation computes a different key, so it starts its own exchange
 *   instead of joining one whose answer is already known to be obsolete;
 * - an exchange started before an invalidation can be recognised as obsolete when it returns, by
 *   comparing the generation it was started in against the one its request is on now.
 *
 * Ordering alone answers neither. A lock establishes which of an invalidation and a write happened
 * first; it cannot establish that a response which left the server before the invalidation is still
 * the right answer, because it never was.
 */
internal data class CalendarScreenExchangeKey(
    val request: CalendarScreenRequest,
    /**
     * How many times [request] has been invalidated. Only ever compared for equality against the
     * request's current generation — the number itself means nothing outside this layer.
     */
    val generation: Int,
)

/**
 * The live state of one observed request, the generation it is currently on, and the only code that
 * decides how the two axes of that state combine.
 *
 * The state and the generation live together because they are read and written together under one
 * lock: whether an answer may be published is a question about both, and splitting them across two
 * tables would make that decision two lookups that can disagree.
 *
 * The flow itself is not handed out. Every change goes through one of the operations below, each of
 * which names an event rather than a value, so the rule that a refresh must never blank a screen
 * that has already loaded is stated once here instead of at every call site that publishes.
 */
private class ObservedScreen {

    private val published =
        MutableStateFlow<CalendarScreenQueryState>(
            CalendarScreenQueryState.Pending(isFetching = false)
        )

    /** Everything published for this request, starting from whatever it is showing now. */
    val states: StateFlow<CalendarScreenQueryState> = published.asStateFlow()

    /**
     * Only ever read or written under the query's guard, which is what makes a plain `var` safe
     * here on Kotlin/Native as well as the JVM.
     */
    private var generation: Int = 0

    /** Whether anyone is collecting [states] right now. */
    val isBeingCollected: Boolean
        get() = published.subscriptionCount.value > 0

    /** The key an exchange for [request] started now belongs to. */
    fun currentExchangeKey(request: CalendarScreenRequest): CalendarScreenExchangeKey =
        CalendarScreenExchangeKey(request, generation)

    /** Whether an exchange started under [key] is still the answer this request is waiting for. */
    fun isCurrent(key: CalendarScreenExchangeKey): Boolean = key.generation == generation

    /** Rules out every exchange already in flight for this request. */
    fun startNewGeneration() {
        generation++
    }

    /** Publishes [response] as a screen that was remembered rather than exchanged for. */
    fun publishRemembered(response: CalendarScreenResponse, isFetching: Boolean) {
        published.value =
            CalendarScreenQueryState.Loaded(
                response = response,
                servedFromCache = true,
                isFetching = isFetching,
            )
    }

    /** Publishes [response] as a screen an exchange just delivered, and calls the network idle. */
    fun publishDelivered(response: CalendarScreenResponse) {
        published.value =
            CalendarScreenQueryState.Loaded(
                response = response,
                servedFromCache = false,
                isFetching = false,
            )
    }

    /**
     * Publishes [error] against whatever screen is currently showing, which stays showing.
     *
     * The screen is carried forward rather than started over, so two failures in a row do not lose
     * the screen the first one was still displaying.
     */
    fun publishFailure(error: Exception) {
        published.update {
            CalendarScreenQueryState.Failed(
                error = error,
                lastLoadedResponse = it.responseStillShowing(),
                isFetching = false,
            )
        }
    }

    /**
     * Raises the in-flight flag over whatever is currently showing, and changes nothing else.
     *
     * The point is what it does *not* do: a state already showing a screen keeps showing it while
     * the flag is up. Replacing it with a fresh [CalendarScreenQueryState.Pending] instead is the
     * collapse-to-skeleton the state's two independent axes exist to make impossible.
     */
    fun markFetching() {
        published.update {
            when (it) {
                is CalendarScreenQueryState.Pending -> it.copy(isFetching = true)
                is CalendarScreenQueryState.Loaded -> it.copy(isFetching = true)
                is CalendarScreenQueryState.Failed -> it.copy(isFetching = true)
            }
        }
    }

    private fun CalendarScreenQueryState.responseStillShowing(): CalendarScreenResponse? =
        when (this) {
            is CalendarScreenQueryState.Pending -> null
            is CalendarScreenQueryState.Loaded -> response
            is CalendarScreenQueryState.Failed -> lastLoadedResponse
        }
}
