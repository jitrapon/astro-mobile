package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.Result
import io.jitrapon.astro.data.network.NonSuccessHttpStatusException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Reads calendar screens from the backend.
 *
 * [httpClient] must have content negotiation installed with the JSON codec this client's models
 * decode through; this class owns the request the backend sees and the [Result] the caller gets,
 * not the transport's configuration.
 *
 * [baseUrl] is injected — origin and scheme belong to the environment the app was built for, and a
 * default baked in here would ship one environment's host to all of them.
 */
class CalendarScreenApi(private val httpClient: HttpClient, baseUrl: String) {

    private val calendarScreenUrl: String = baseUrl.trimEnd('/') + CALENDAR_SCREEN_PATH

    /**
     * Fetches one calendar screen.
     *
     * Every failure a caller can act on arrives as [Result.Error] rather than as a thrown
     * exception: no response, a non-2xx status, or a body that does not decode.
     *
     * Cancellation is the one exception, and it is deliberately NOT one of those failures — it
     * propagates out untouched. A cancelled fetch has no caller left to answer: converting it into
     * an error would hand a superseded request's failure to a caller that has already moved on to a
     * different range or view, and would break the cancellation of every coroutine waiting above.
     */
    suspend fun fetchCalendarScreen(
        request: CalendarScreenRequest
    ): Result<CalendarScreenResponse> {
        val exchange = runCatching { requestCalendarScreen(request) }
        return exchange.fold(onSuccess = { Result.Success(it) }, onFailure = { asFetchError(it) })
    }

    private suspend fun requestCalendarScreen(
        request: CalendarScreenRequest
    ): CalendarScreenResponse {
        val response =
            httpClient.get(calendarScreenUrl) {
                parameter("view", request.view.wireValue)
                parameter("start", request.start.toIsoDate())
                parameter("end", request.end.toIsoDate())
                parameter("tz", request.timeZone)
                parameter("locale", request.locale)
                // Ktor's `parameter` drops a null value, so an unset optional is absent from the
                // query string rather than sent empty — which the backend would read as a supplied
                // value that fails its pattern.
                parameter("dayCount", (request.view as? RequestedCalendarView.TimeGrid)?.dayCount)
                parameter("knownTheme", request.knownTheme)
            }
        if (!response.status.isSuccess()) {
            throw NonSuccessHttpStatusException(response.status.value, response.status.description)
        }
        return response.body()
    }

    /**
     * Maps a failed exchange onto [Result.Error], letting through the two kinds of throwable that
     * are not this client's to report: cancellation (see [fetchCalendarScreen]) and anything that
     * is not an [Exception] at all, which signals the runtime is in a state no error branch can
     * repair.
     */
    private fun asFetchError(failure: Throwable): Result<Nothing> {
        if (failure is CancellationException || failure !is Exception) throw failure
        return Result.Error(failure)
    }

    private companion object {
        const val CALENDAR_SCREEN_PATH = "/screens/calendar"
    }
}
