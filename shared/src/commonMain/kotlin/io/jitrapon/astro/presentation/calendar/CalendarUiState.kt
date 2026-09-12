package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.CalendarScreenQueryState
import io.jitrapon.astro.data.calendar.CalendarScreenResponse

/**
 * Everything a calendar screen needs to paint itself, in one value: what there is to show, whether
 * the network is busy, and what went wrong if anything did.
 *
 * It is deliberately not [CalendarScreenQueryState] handed upwards. That type answers the data
 * layer's questions and carries the data layer's vocabulary, and two of its properties would cost a
 * renderer something to consume:
 * - **the three cases force a downcast** to answer either of the questions a renderer asks first —
 *   "is there anything to paint" and "is the network busy" — so every screen would repeat the same
 *   three-way match to reach a two-field answer;
 * - **[CalendarScreenQueryState.Loaded.servedFromCache] is provenance, not appearance.** A
 *   remembered screen and an identical fetched one must render identically, so the flag exists for
 *   the layer that decides whether to exchange, and a renderer that could see it could branch on
 *   it. It stops here.
 *
 * What does not stop here is the screen behind a failure. [content] is filled from
 * [CalendarScreenQueryState.Failed.lastLoadedResponse] as readily as from a loaded screen, so the
 * pair a caller branches on — content present, failure present — can express an error banner over
 * live content and not only a full error screen.
 */
data class CalendarUiState(
    /**
     * The screen to paint and the envelope it arrived in, or `null` when nothing has loaded yet.
     *
     * The whole [CalendarScreenResponse] rather than the screen inside it: the envelope carries the
     * active theme and the server's own clock, and a client derives "today" from that clock and
     * never from the device's.
     */
    val content: CalendarScreenResponse?,
    /**
     * Whether an exchange is in flight — a first load and a refresh over existing content alike.
     *
     * Independent of [content] on purpose: true with nothing showing is the case for a skeleton,
     * true over a screen the case for a progress indicator on top of it. It never means the content
     * beside it is wrong.
     */
    val isLoading: Boolean,
    /**
     * What the most recent exchange failed with, or `null` when it did not fail.
     *
     * The exception itself rather than a message, because turning one into text a person reads
     * needs the platform's localisation and this module has none. A cancelled observation never
     * arrives here: cancellation is not a failure.
     */
    val failure: Exception?,
)

/**
 * Projects one observed state onto what a renderer paints.
 *
 * The only place the mapping is stated. Both the failure branch keeping the screen it landed over
 * and the in-flight flag surviving every case are rules that would otherwise be re-decided — and
 * eventually contradicted — at each screen that consumes an observation.
 */
internal fun CalendarScreenQueryState.toCalendarUiState(): CalendarUiState =
    when (this) {
        is CalendarScreenQueryState.Pending ->
            CalendarUiState(content = null, isLoading = isFetching, failure = null)
        is CalendarScreenQueryState.Loaded ->
            CalendarUiState(content = response, isLoading = isFetching, failure = null)
        is CalendarScreenQueryState.Failed ->
            CalendarUiState(content = lastLoadedResponse, isLoading = isFetching, failure = error)
    }
