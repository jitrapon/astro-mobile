package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.Result

/**
 * The calendar screen's data boundary: everything above it asks this type for a screen, and nothing
 * above it holds a reference to [CalendarScreenApi] or knows a screen arrives over HTTP.
 *
 * **It holds no state.** There is no cached screen, no in-flight request tracking, and no refresh
 * policy here, and that is a decision rather than an omission. A cache at this boundary has to key
 * on the whole of [CalendarScreenRequest] — a screen fetched for one view, range, time zone,
 * locale, day count, or known theme answers no other request — and it has to decide what happens
 * when a slower earlier response lands after a newer one. Get either wrong and this type hands its
 * caller a screen belonging to a different range or view, which reads as a rendering bug
 * arbitrarily far away. Both questions are answerable only against a consumer that defines what a
 * superseded request means; until one exists, a cache here would be untested machinery guarding
 * against nothing.
 *
 * Adding one later is a change confined to this class: callers already treat every fetch as a
 * suspending request for the screen matching a request, which is equally true of a cached answer.
 */
class CalendarScreenRepository(private val calendarScreenApi: CalendarScreenApi) {

    /**
     * Fetches the screen matching [request], forwarding the API client's [Result] unchanged.
     *
     * Failures stay [Result.Error] and cancellation still propagates — this boundary neither
     * repairs, retries, nor reinterprets what the client returned.
     */
    suspend fun fetchCalendarScreen(
        request: CalendarScreenRequest
    ): Result<CalendarScreenResponse> = calendarScreenApi.fetchCalendarScreen(request)
}
