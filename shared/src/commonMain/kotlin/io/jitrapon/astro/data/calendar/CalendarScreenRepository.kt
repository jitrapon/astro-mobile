package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.Result
import kotlinx.coroutines.flow.Flow

/**
 * The calendar screen's data boundary: everything above it asks this type for a screen, and nothing
 * above it holds a reference to [CalendarScreenApi] or knows a screen arrives over HTTP.
 *
 * **What it remembers is keyed on the whole request.** An observed screen is served from a cache
 * whose key is every field of [CalendarScreenRequest] — the view, the window, the zone, the locale,
 * the day count, the known theme — so a response is only ever published under the request that
 * asked for it, and two observations differing in any field share neither a screen nor an exchange.
 * That is the guarantee callers above this boundary may rely on. Which of a remembered screen and a
 * newer one is shown while an exchange runs, how many exchanges concurrent observers cause, and
 * what a failed refresh does to a screen that had already loaded are [CalendarScreenQuery]'s
 * decisions; this type names them in its own vocabulary and adds nothing to them.
 *
 * [fetchCalendarScreen] is outside that arrangement entirely: it neither reads the cache nor writes
 * to it, so a one-shot call always costs an exchange and can never be answered with a screen it did
 * not itself fetch.
 *
 * The type is public — it is what iOS and Android resolve from the graph — but its constructor is
 * not, because [CalendarScreenApi] is internal and a public constructor taking one would put Ktor
 * on the iOS framework's generated surface. The graph is the only thing that builds one.
 *
 * The three observation members are `internal` for the same reason the constructor is: an
 * observation is a [Flow], and every public member of this class becomes a member of the generated
 * Objective-C header, where a non-exported library type still arrives under a mangled name. Swift
 * reaches this seam through the iOS adapter, which turns a subscription into something Objective-C
 * can drive; Android reaches it through the shared view model above this type. Both live in this
 * module, where `internal` is visible. [fetchCalendarScreen] stays public because it is already
 * part of that header and carries no library type.
 */
class CalendarScreenRepository
internal constructor(
    private val calendarScreenApi: CalendarScreenApi,
    private val calendarScreenQuery: CalendarScreenQuery,
) {

    /**
     * Fetches the screen matching [request], forwarding the API client's [Result] unchanged.
     *
     * Failures stay [Result.Error] and cancellation still propagates — this boundary neither
     * repairs, retries, nor reinterprets what the client returned.
     */
    suspend fun fetchCalendarScreen(
        request: CalendarScreenRequest
    ): Result<CalendarScreenResponse> = calendarScreenApi.fetchCalendarScreen(request)

    /**
     * Observes the screen matching [request]: what is showing now, then every state that replaces
     * it, for as long as the caller collects.
     *
     * Never completes — only the collector's own scope ends it — and a collector that goes away
     * receives nothing further, in particular never a failure it did not cause. Collecting is what
     * starts the work, so re-observing a request is how a caller asks for it to be brought up to
     * date; observing it twice while what is remembered is still fresh costs no exchange at all.
     */
    internal fun observeCalendarScreen(
        request: CalendarScreenRequest
    ): Flow<CalendarScreenQueryState> = calendarScreenQuery.observeScreen(request)

    /**
     * Exchanges for a newer screen matching [request] whether or not the remembered one still
     * stands, and publishes the outcome to whoever is observing that request.
     *
     * Reports nothing: a refresh's result is read from the observed state, which is the one place
     * it is ever published. Suspends until the exchange it joined has been dealt with, so a caller
     * can sequence work behind a completed refresh.
     */
    internal suspend fun refetchCalendarScreen(request: CalendarScreenRequest) {
        calendarScreenQuery.refetchScreen(request)
    }

    /**
     * Declares every remembered screen whose request satisfies [matches] to be wrong: it is dropped
     * so the next observation cannot be served it, an exchange already in flight for it can no
     * longer publish what it brings back, and anyone currently observing one is refreshed rather
     * than left painting content that is known to be stale.
     *
     * A predicate rather than a request so a caller can invalidate a family — every screen for a
     * calendar, a zone, a range — without knowing which requests were ever observed.
     */
    internal suspend fun invalidateCalendarScreens(matches: (CalendarScreenRequest) -> Boolean) {
        calendarScreenQuery.invalidateScreens(matches)
    }
}
