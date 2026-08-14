package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.Result
import io.jitrapon.astro.data.network.createBackendHttpClient
import io.jitrapon.astro.data.network.createLenientBackendJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.fail

/**
 * The origin the stubbed backend answers on.
 *
 * `https` deliberately: a cleartext `http://` literal anywhere in Kotlin is a security-lint
 * finding, and a test fixture is no exception to a rule whose whole point is that no such literal
 * exists to be copied into production.
 */
internal const val TEST_BACKEND_BASE_URL: String = "https://calendar.astro.test"

/**
 * The calendar backend, stubbed and recording.
 *
 * Substituting Ktor's [MockEngine] for a real engine is what makes both halves of an exchange
 * assertable: [requests] holds what the client actually put on the wire — the assertion no amount
 * of reading the client's source can replace — while [respondTo] decides what comes back, so
 * response handling is exercised without a network or a live backend.
 *
 * Everything above the engine is the production stack: the same client factory, the same lenient
 * codec, the same [CalendarScreenApi]. A test double for any of those would be testing itself.
 */
internal class MockCalendarBackend(
    baseUrl: String = TEST_BACKEND_BASE_URL,
    /**
     * Suspending so a case can hold the exchange open on a suspension point — the only way to
     * observe what the client does with a fetch that is cancelled mid-flight.
     */
    respondTo: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respondWithMonthScreenFixture()
    },
) {

    private val recordedRequests = mutableListOf<HttpRequestData>()

    /** Every request the client emitted, in the order it emitted them. */
    val requests: List<HttpRequestData>
        get() = recordedRequests

    private val engine = MockEngine { request ->
        recordedRequests += request
        respondTo(request)
    }

    val calendarScreenApi: CalendarScreenApi =
        CalendarScreenApi(
            httpClient = createBackendHttpClient(engine, createLenientBackendJson()),
            baseUrl = baseUrl,
        )

    val calendarScreenRepository: CalendarScreenRepository =
        CalendarScreenRepository(calendarScreenApi)
}

/** Answers with [body] as JSON, so content negotiation decodes it rather than refusing the type. */
internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData =
    respond(
        body,
        status,
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

/** Answers with the vendored month-screen fixture — a real, contract-shaped success response. */
internal fun MockRequestHandleScope.respondWithMonthScreenFixture(): HttpResponseData =
    respondJson(monthScreenFixtureJson().toString())

/** A minimal well-formed month request: no day count, no known theme. */
internal fun monthScreenRequest(
    view: RequestedCalendarView = RequestedCalendarView.Month,
    knownTheme: String? = null,
): CalendarScreenRequest =
    CalendarScreenRequest(
        view = view,
        start = CalendarDate(year = 2026, month = 4, dayOfMonth = 1),
        end = CalendarDate(year = 2026, month = 4, dayOfMonth = 30),
        timeZone = "Asia/Bangkok",
        locale = "th-TH",
        knownTheme = knownTheme,
    )

/** Runs [request] against a stubbed backend and returns the request the client put on the wire. */
internal suspend fun emittedRequestFor(request: CalendarScreenRequest): HttpRequestData {
    val backend = MockCalendarBackend()
    backend.calendarScreenApi.fetchCalendarScreen(request)
    return backend.requests.single()
}

/**
 * The screen a fetch delivered, failing the test — with the reported exception — when it did not.
 *
 * Unwrapping through this rather than casting is what puts the reason a fetch failed into the
 * failure message: a cast reports only that the outcome was the wrong branch, which says nothing
 * about the status, the decode, or the version that produced it.
 */
internal fun deliveredScreen(outcome: Result<CalendarScreenResponse>): CalendarScreenResponse =
    when (outcome) {
        is Result.Success -> outcome.data
        is Result.Error ->
            fail("Expected a delivered screen, but the fetch reported an error.", outcome.exception)
    }

/**
 * The exception a fetch reported as [T], failing the test when it succeeded or failed otherwise.
 */
internal inline fun <reified T : Exception> reportedFailure(
    outcome: Result<CalendarScreenResponse>
): T =
    when (outcome) {
        is Result.Success ->
            fail(
                "Expected the fetch to report a ${T::class.simpleName}, but it delivered a screen."
            )
        is Result.Error ->
            outcome.exception as? T
                ?: fail(
                    "Expected the fetch to report a ${T::class.simpleName}.",
                    outcome.exception,
                )
    }
