package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.Result
import io.jitrapon.astro.data.network.BackendRequestDeadlines
import io.jitrapon.astro.data.network.NonSuccessHttpStatusException
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Drives whole exchanges through a stubbed backend and asserts on both of their halves: the request
 * the client emitted and the outcome it handed back.
 *
 * Either half alone can be right while the exchange is wrong. A client that builds a flawless query
 * and then reports a decoded screen as an error is as broken as one that decodes perfectly from a
 * request the backend would reject, and only a case that pins the request and the outcome together
 * rules both out.
 *
 * The failure cases matter as much as the successful one. Every way a fetch can fail — a status
 * outside 2xx, a body that does not decode, an envelope from a contract version these models were
 * not written against — must reach the caller as [io.jitrapon.astro.data.Result.Error], because a
 * failure that escapes as a throw instead is one a call site can forget to handle. Cancellation is
 * the deliberate exception and has its own case below.
 */
class CalendarScreenApiExchangeTest {

    @Test
    fun asksForADateOnlyWindowWithNoDayCountAndDeliversTheMonthScreen() = runTest {
        val backend = MockCalendarBackend()

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        val emitted = backend.requests.single()
        assertEquals("month", emitted.url.parameters["view"])
        assertEquals("2026-04-01", emitted.url.parameters["start"])
        assertEquals("2026-04-30", emitted.url.parameters["end"])
        assertNull(
            emitted.url.parameters[DAY_COUNT],
            "A month screen has no columns, so a day count would be a value the backend ignores.",
        )
        assertEquals(SUPPORTED_SCHEMA_VERSION, deliveredScreen(outcome).schemaVersion)
    }

    @Test
    fun asksForTheColumnCountOnATimeGridCallAndDeliversTheScreen() = runTest {
        val backend = MockCalendarBackend()

        val outcome =
            backend.calendarScreenApi.fetchCalendarScreen(
                monthScreenRequest(view = RequestedCalendarView.TimeGrid(dayCount = 3))
            )

        assertEquals("3", backend.requests.single().url.parameters[DAY_COUNT])
        assertEquals(SUPPORTED_SCHEMA_VERSION, deliveredScreen(outcome).schemaVersion)
    }

    @Test
    fun leavesKnownThemeOutOfTheQueryStringWhenTheCallerHoldsNoTheme() = runTest {
        val backend = MockCalendarBackend()

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        // Absent, not empty. `?knownTheme=` is a supplied value as far as the backend is concerned,
        // and it would fail the parameter's pattern rather than read as "the caller holds none".
        val emitted = backend.requests.single()
        assertFalse(emitted.url.encodedQuery.contains(KNOWN_THEME))
        assertNotNull(deliveredScreen(outcome).themeDocument)
    }

    @Test
    fun deliversACacheHitResponseWithItsThemeReferenceIntact() = runTest {
        // What the backend returns when the request's `knownTheme` already named the active theme:
        // the reference, and no document. The common case in production, and the one a client that
        // treated the document as required would break on.
        val backend = MockCalendarBackend {
            respondWithMonthScreenEnvelope { envelope -> envelope.without("themeDocument") }
        }

        val outcome =
            backend.calendarScreenApi.fetchCalendarScreen(
                monthScreenRequest(knownTheme = KNOWN_THEME_REFERENCE)
            )

        assertEquals(KNOWN_THEME_REFERENCE, backend.requests.single().url.parameters[KNOWN_THEME])
        val screen = deliveredScreen(outcome)
        assertNull(screen.themeDocument)
        assertEquals(decodeMonthScreenFixture().theme, screen.theme)
    }

    @Test
    fun dropsAThemeDocumentThatDescribesADifferentThemeThanTheEnvelopeDeclares() = runTest {
        // Both halves of the pair are individually valid — this is why the schema cannot catch it.
        // The tokens in the document were resolved against a different theme than the per-calendar
        // colour triples in the body, so applying them would paint one theme's colours over the
        // other's.
        val backend = MockCalendarBackend {
            respondWithMonthScreenEnvelope { envelope ->
                envelope.replacing(
                    "themeDocument",
                    envelope
                        .themeDocument()
                        .replacing("version", JsonPrimitive("a-different-hash")),
                )
            }
        }

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        // Dropped, not failed: the theme reference is still trustworthy and the screen is still
        // worth showing, so the caller falls back to the theme it holds — the same path it takes on
        // the far more common cache hit.
        val screen = deliveredScreen(outcome)
        assertNull(screen.themeDocument)
        assertEquals(decodeMonthScreenFixture().theme, screen.theme)
    }

    @Test
    fun keepsAThemeDocumentThatDescribesTheDeclaredTheme() = runTest {
        // The guard above must discriminate rather than simply discard: a matching document is the
        // whole point of inline delivery, and dropping it would send every caller back for a fetch
        // the response already answered.
        val backend = MockCalendarBackend()

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        val screen = deliveredScreen(outcome)
        val document = assertNotNull(screen.themeDocument)
        assertEquals(screen.theme.id, document.id)
        assertEquals(screen.theme.version, document.version)
    }

    @Test
    fun reportsAStatusOutsideTwoHundredsAsAnErrorCarryingIt() = runTest {
        val backend = MockCalendarBackend {
            respondJson("{}", status = HttpStatusCode.ServiceUnavailable)
        }

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        // The status is carried as data, not only in prose: a caller distinguishes a range the
        // backend refuses from one it is briefly unable to serve by branching on it.
        assertEquals(
            HttpStatusCode.ServiceUnavailable.value,
            reportedFailure<NonSuccessHttpStatusException>(outcome).statusCode,
        )
    }

    @Test
    fun reportsABodyThatDoesNotDecodeAsAnError() = runTest {
        val backend = MockCalendarBackend { respondJson("{\"schemaVersion\":") }

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        // The point is the branch, not the exception type: a decode failure must arrive as an
        // outcome the caller has to handle rather than as a throw out of a suspend call.
        reportedFailure<Exception>(outcome)
    }

    @Test
    fun reportsASchemaVersionOnEitherSideOfTheSupportedOneAsAnErrorNamingBoth() = runTest {
        // Both directions, because they are different product states: a backend ahead of this build
        // means the app needs updating, while a build ahead of its backend is a rollout ordering
        // bug. Neither response is readable, however well-formed the rest of it is.
        listOf("0.1.0", "0.3.0").forEach { unsupportedVersion ->
            val backend = MockCalendarBackend {
                respondWithMonthScreenEnvelope { envelope ->
                    envelope.replacing("schemaVersion", JsonPrimitive(unsupportedVersion))
                }
            }

            val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

            val failure = reportedFailure<UnsupportedSchemaVersionException>(outcome)
            assertEquals(unsupportedVersion, failure.receivedSchemaVersion)
            assertEquals(SUPPORTED_SCHEMA_VERSION, failure.supportedSchemaVersion)
            assertContains(failure.message.orEmpty(), unsupportedVersion)
            assertContains(failure.message.orEmpty(), SUPPORTED_SCHEMA_VERSION)
        }
    }

    @Test
    fun reportsABackendThatNeverAnswersAsAnErrorInsteadOfWaitingForever() = runTest {
        // Accepted and then silent — the failure mode a status code cannot express. Without a
        // deadline in the shared client this fetch never returns, and the only thing bounding it is
        // whatever the platform's engine happens to default to, which is not the same number on
        // both. The engine here defaults to nothing at all, so the assertion is on the client's own
        // deadline.
        val neverAnswered = CompletableDeferred<HttpResponseData>()
        val backend =
            // Short deadline because what is under test is that one is enforced at all, not what
            // the shipped policy is set to. Waiting out the real one would spend half a minute per
            // target proving the same thing.
            MockCalendarBackend(deadlines = BackendRequestDeadlines(requestMillis = 50L)) {
                neverAnswered.await()
            }

        val outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())

        // An abandoned request is a failure the caller must handle, not a throw out of a suspend
        // call and not a cancellation — nothing cancelled this fetch; it ran out of time.
        reportedFailure<HttpRequestTimeoutException>(outcome)
    }

    @Test
    fun letsCancellationReachTheCallerInsteadOfReportingItAsAnOutcome() = runTest {
        val exchangeStarted = CompletableDeferred<Unit>()
        val neverAnswered = CompletableDeferred<HttpResponseData>()
        val backend = MockCalendarBackend {
            exchangeStarted.complete(Unit)
            neverAnswered.await()
        }
        var outcome: Result<CalendarScreenResponse>? = null

        val fetch = async {
            outcome = backend.calendarScreenApi.fetchCalendarScreen(monthScreenRequest())
        }
        exchangeStarted.await()
        fetch.cancel()

        assertFailsWith<CancellationException> { fetch.await() }
        // A cancelled fetch has no caller left to answer. Converting it into an error would hand a
        // superseded range or view's failure to a caller that has already moved on, and would stop
        // the cancellation from reaching the coroutines waiting above this one.
        assertNull(outcome, "A cancelled fetch produced an outcome: $outcome")
    }
}

private const val DAY_COUNT = "dayCount"

private const val KNOWN_THEME = "knownTheme"

private const val KNOWN_THEME_REFERENCE = "light@8a95f0d5c35fdec5eab641a121ae068f"

/** Answers with the vendored month-screen fixture after [edit] has perturbed its envelope. */
private fun MockRequestHandleScope.respondWithMonthScreenEnvelope(
    edit: (JsonObject) -> JsonObject
): HttpResponseData = respondJson(edit(monthScreenFixtureJson()).toString())

/**
 * This envelope's theme document, failing the test when it carries none.
 *
 * A case that perturbs the document has nothing to perturb without it, and would otherwise quietly
 * assert the cache-hit path instead of the one it was written for.
 */
private fun JsonObject.themeDocument(): JsonObject =
    this["themeDocument"] as? JsonObject
        ?: fail("The month-screen fixture carries no themeDocument to perturb.")
