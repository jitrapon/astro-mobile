package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.network.NonSuccessHttpStatusException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

/**
 * Holds [CalendarScreenRepository] to being a forwarding boundary and nothing more.
 *
 * The repository exists so nothing above it reaches past it into the API client; what it must not
 * do yet is remember anything. The last case is what pins that: it asks for two different screens
 * and requires each answer to belong to its own request. A cache added here later without keying on
 * the whole request — the view, the window, the zone, the locale, the day count, the known theme —
 * fails that case instead of quietly serving one range's screen to a caller that asked for another.
 */
class CalendarScreenRepositoryTest {

    @Test
    fun forwardsTheDeliveredScreenUnchanged() = runTest {
        val backend = MockCalendarBackend()

        val outcome = backend.calendarScreenRepository.fetchCalendarScreen(monthScreenRequest())

        // Equal to what the fixture decodes to independently: the repository adds no field, drops
        // none, and rewrites none on the way through.
        assertEquals(decodeMonthScreenFixture(), deliveredScreen(outcome))
        assertEquals(1, backend.requests.size)
    }

    @Test
    fun forwardsAReportedFailureUnchanged() = runTest {
        val backend = MockCalendarBackend { respondJson("{}", status = HttpStatusCode.NotFound) }

        val outcome = backend.calendarScreenRepository.fetchCalendarScreen(monthScreenRequest())

        // Not repaired, not retried, not reinterpreted — the same failure the client reported,
        // still carrying the status a caller would branch on.
        assertEquals(
            HttpStatusCode.NotFound.value,
            reportedFailure<NonSuccessHttpStatusException>(outcome).statusCode,
        )
        assertEquals(1, backend.requests.size)
    }

    @Test
    fun answersEachOfTwoSuccessiveRequestsWithItsOwnScreen() = runTest {
        // The stub echoes the requested zone back in the envelope, so a screen belonging to the
        // wrong request is visible in the response rather than only in the request log.
        val backend = MockCalendarBackend { request ->
            respondJson(
                monthScreenFixtureJson()
                    .replacing(
                        "timeZone",
                        JsonPrimitive(request.url.parameters["tz"].orEmpty()),
                    )
                    .toString()
            )
        }
        val repository = backend.calendarScreenRepository

        val bangkok = repository.fetchCalendarScreen(monthScreenRequest())
        val zurich =
            repository.fetchCalendarScreen(monthScreenRequest().copy(timeZone = ZURICH_TIME_ZONE))

        assertEquals(BANGKOK_TIME_ZONE, deliveredScreen(bangkok).timeZone)
        assertEquals(ZURICH_TIME_ZONE, deliveredScreen(zurich).timeZone)
        assertEquals(2, backend.requests.size, "A second request was answered without being sent.")
    }
}

/** The zone [monthScreenRequest] asks for, and so the one the first response must come back in. */
private const val BANGKOK_TIME_ZONE = "Asia/Bangkok"

private const val ZURICH_TIME_ZONE = "Europe/Zurich"
