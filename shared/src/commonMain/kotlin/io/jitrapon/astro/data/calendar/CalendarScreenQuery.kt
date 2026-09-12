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
        SingleFlightRunner<CalendarScreenRequest, Result<CalendarScreenResponse>>,
    private val ticker: Ticker,
    private val scope: CoroutineScope,
) {

    /**
     * Guards [observations] across find-or-create, so two observers arriving together cannot each
     * conclude they are the first and walk away holding two different states for one request.
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
    private val observations = mutableMapOf<CalendarScreenRequest, ObservedScreenState>()

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
        val state = guard.withLock { observations.getOrPut(request, ::newObservation) }
        // The load is started from `onSubscription` rather than before `emitAll` so that it cannot
        // begin until this collector is registered with the state. Started any earlier, a load that
        // reached its first publication before registration would have that state conflated away,
        // and a collector would silently miss the transition it was attached to see.
        emitAll(state.onSubscription { scope.launch { serveAndRefresh(request, state) } })
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
     */
    private suspend fun serveAndRefresh(
        request: CalendarScreenRequest,
        state: ObservedScreenState,
    ) {
        val remembered = screenCache.read(request)
        val verdict = remembered?.let {
            screenCachePolicy.classifyCachedScreen(it, ticker.readTickNanos())
        }
        val exchangeNeeded = verdict != ScreenCacheVerdict.FRESH
        val showable = remembered?.takeIf { verdict != ScreenCacheVerdict.UNUSABLE }
        if (showable != null) {
            state.value =
                CalendarScreenQueryState.Loaded(
                    response = showable.screen,
                    servedFromCache = true,
                    isFetching = exchangeNeeded,
                )
        } else {
            // Nothing showable means nothing was remembered, or what was is unreadable to this
            // build — either way an exchange is coming, so the flag goes up over whatever is
            // already there rather than replacing it.
            state.update { it.withFetching(isFetching = true) }
        }
        if (exchangeNeeded) exchangeAndPublish(request, state)
    }

    /**
     * Runs one exchange for [request] through [singleFlightRunner] and publishes what it produced.
     *
     * Concurrent observers of one request reach this together and share the single execution the
     * runner starts, so they cause one round trip and publish one screen between them.
     *
     * A failure publishes [CalendarScreenQueryState.Failed] carrying whatever was showing when it
     * landed, so a failed refresh reports itself without discarding a screen that loaded perfectly
     * well. Cancellation reaches neither branch: it propagates out of the API client untouched
     * rather than arriving as an error, so a superseded exchange publishes nothing at all.
     */
    private suspend fun exchangeAndPublish(
        request: CalendarScreenRequest,
        state: ObservedScreenState,
    ) {
        when (val outcome = singleFlightRunner.runOnce(request) { fetchAndRemember(request) }) {
            is Result.Success ->
                state.value =
                    CalendarScreenQueryState.Loaded(
                        response = outcome.data,
                        servedFromCache = false,
                        isFetching = false,
                    )
            is Result.Error ->
                state.update {
                    CalendarScreenQueryState.Failed(
                        error = outcome.exception,
                        lastLoadedResponse = it.responseStillShowing(),
                        isFetching = false,
                    )
                }
        }
    }

    /**
     * Fetches the screen matching [request] and remembers a delivered one.
     *
     * The write lives here — inside the work one exchange runs — rather than beside the publication
     * above it, so a screen is remembered exactly once per round trip and stamped with the tick at
     * which it actually arrived. Publishing is per-observer and would otherwise re-file the same
     * response once per collector, each write making the entry look newer than the exchange that
     * produced it.
     *
     * It does not live in [CalendarScreenApi] either: that client's job ends at turning an exchange
     * into a [Result], and a client that wrote to a cache could not be used for an exchange whose
     * answer should not be remembered.
     */
    private suspend fun fetchAndRemember(
        request: CalendarScreenRequest
    ): Result<CalendarScreenResponse> {
        val outcome = calendarScreenApi.fetchCalendarScreen(request)
        if (outcome is Result.Success) screenCache.write(request, outcome.data)
        return outcome
    }

    private fun newObservation(): ObservedScreenState =
        MutableStateFlow(CalendarScreenQueryState.Pending(isFetching = false))
}

/**
 * The live state of one observed request.
 *
 * An alias rather than the type spelled out at each use: the three-parameter spelling appears in
 * every signature in this file and says nothing more than the name does.
 */
private typealias ObservedScreenState = MutableStateFlow<CalendarScreenQueryState>

/**
 * Returns this state with its in-flight flag set to [isFetching] and everything else untouched.
 *
 * The point is what it does *not* do: a state that is already showing a screen keeps showing it
 * while the flag is raised. Replacing it with a fresh [CalendarScreenQueryState.Pending] instead is
 * the collapse-to-skeleton this type's two axes exist to make impossible.
 */
private fun CalendarScreenQueryState.withFetching(isFetching: Boolean): CalendarScreenQueryState =
    when (this) {
        is CalendarScreenQueryState.Pending -> copy(isFetching = isFetching)
        is CalendarScreenQueryState.Loaded -> copy(isFetching = isFetching)
        is CalendarScreenQueryState.Failed -> copy(isFetching = isFetching)
    }

/**
 * The response a collector of this state is currently painting, or `null` when there is none.
 *
 * A failure carries it forward rather than starting over: two failures in a row must not lose the
 * screen the first one was still showing.
 */
private fun CalendarScreenQueryState.responseStillShowing(): CalendarScreenResponse? =
    when (this) {
        is CalendarScreenQueryState.Pending -> null
        is CalendarScreenQueryState.Loaded -> response
        is CalendarScreenQueryState.Failed -> lastLoadedResponse
    }
