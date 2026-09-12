package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.network.NonSuccessHttpStatusException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pins what one request's observation publishes over time: what it paints before the network
 * answers, how many round trips several observers cause between them, and what a failure does to a
 * screen that had already loaded.
 *
 * Two of the properties here are invisible in a final state alone, which is why these cases read
 * sequences and count exchanges rather than only checking what was left showing. A layer that
 * blanked the screen on every refresh and one that painted straight through it end on the same
 * state; so do a layer that collapses concurrent observers onto one exchange and one that fetches
 * once per observer.
 *
 * Staleness is crossed by advancing virtual time and an exchange is held open on a gate, so nothing
 * here waits on a real clock. What a case *cannot* drive is the exchange itself — it travels
 * through Ktor's engine on a real dispatcher — so a case that needs one to have answered waits for
 * the state it produces rather than for the test scheduler to drain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarScreenQueryObservationTest {

    @Test
    fun aColdObservationPaintsNothingUntilTheExchangeAnswers() = runTest {
        val fixture = CalendarScreenQueryFixture(backgroundScope, testScheduler)

        val observation =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))

        assertEquals(
            CalendarScreenQueryState.Loaded(
                response = decodeMonthScreenFixture(),
                servedFromCache = false,
                isFetching = false,
            ),
            observation.awaitLatest { it is CalendarScreenQueryState.Loaded },
        )
        // Nothing was remembered, so nothing could be painted first: every state before the
        // delivered screen must be Pending. A layer that published an empty screen while it waited
        // would end on the same state and fail here.
        assertTrue(
            observation.states.dropLast(1).all { it is CalendarScreenQueryState.Pending },
            "A screen was published before the exchange answered: ${observation.states}",
        )
        assertEquals(1, fixture.exchanges)
    }

    @Test
    fun aSecondObservationInsideTheWindowIsServedTheRememberedScreenWithoutAnExchange() = runTest {
        val fixture = CalendarScreenQueryFixture(backgroundScope, testScheduler)
        val first = backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        first.awaitLatest { it is CalendarScreenQueryState.Loaded }
        first.stopCollecting()

        val second = backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))

        assertEquals(
            CalendarScreenQueryState.Loaded(
                response = decodeMonthScreenFixture(),
                servedFromCache = true,
                isFetching = false,
            ),
            second.awaitLatest { it is CalendarScreenQueryState.Loaded && it.servedFromCache },
        )
        assertEquals(1, fixture.exchanges, "A remembered screen inside its window was re-fetched.")
    }

    @Test
    fun twoConcurrentObserversOfOneRequestCauseOneExchangeAndSeeTheSameScreen() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondWithMonthScreenFixture()
            }

        val first = backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        val second = backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        // Both reach the query while the exchange is held, which is the arrival order deduplication
        // has to handle — a second observer landing after the first was answered would legitimately
        // start its own exchange and prove nothing.
        runCurrent()
        stalled.release()

        first.awaitLatest { it is CalendarScreenQueryState.Loaded }
        second.awaitLatest { it is CalendarScreenQueryState.Loaded }
        assertEquals(decodeMonthScreenFixture(), loadedResponse(first.latest))
        assertEquals(first.latest, second.latest, "Two observers of one request diverged.")
        assertEquals(1, fixture.exchanges, "One request was exchanged once per observer.")
    }

    @Test
    fun observersOfDifferentRequestsEachGetTheirOwnExchangeAndNeverTheOtherScreen() = runTest {
        // The stub echoes the requested zone back in the envelope, so a screen belonging to the
        // wrong request is visible in what was published rather than only in the request log.
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) { request ->
                respondJson(
                    monthScreenFixtureJson()
                        .replacing(
                            "timeZone",
                            JsonPrimitive(request.url.parameters["tz"].orEmpty()),
                        )
                        .toString()
                )
            }

        val bangkok =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        val zurich =
            backgroundScope.recordStates(
                fixture.query.observeScreen(monthScreenRequest().copy(timeZone = ZURICH_TIME_ZONE))
            )

        bangkok.awaitLatest { it is CalendarScreenQueryState.Loaded }
        zurich.awaitLatest { it is CalendarScreenQueryState.Loaded }
        assertEquals(BANGKOK_TIME_ZONE, loadedResponse(bangkok.latest).timeZone)
        assertEquals(ZURICH_TIME_ZONE, loadedResponse(zurich.latest).timeZone)
        assertEquals(2, fixture.exchanges, "Two different requests were collapsed onto one screen.")
    }

    @Test
    fun anObservationPastTheWindowPaintsTheRememberedScreenWhileTheFreshOneLoads() = runTest {
        val stalled = StalledExchange()
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(
                backgroundScope,
                testScheduler,
                stalenessWindow = STALENESS_WINDOW,
            ) {
                // Only the refresh is held: the first exchange must answer so there is a remembered
                // screen to age at all.
                val nth = ++exchanges
                if (nth > 1) stalled.hold()
                respondWithServerTime(serverTimeOfExchange(nth))
            }
        val first = backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        first.awaitLatest { it is CalendarScreenQueryState.Loaded }
        first.stopCollecting()

        // Held for exactly the window is already past it, so this is the first reading at which the
        // entry no longer stands as the answer.
        testScheduler.advanceTimeBy(STALENESS_WINDOW)
        val refreshing =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        // The remembered screen is published before the exchange is even started, so draining the
        // test scheduler is enough to have it in hand — and asserting here, rather than after the
        // exchange answers, is the only way to catch a layer that skipped straight to a skeleton.
        runCurrent()

        assertEquals(
            CalendarScreenQueryState.Loaded(
                response = decodeMonthScreenFixture().copy(serverTime = serverTimeOfExchange(1)),
                servedFromCache = true,
                isFetching = true,
            ),
            refreshing.latest,
            "The aged screen was not painted while its replacement loaded.",
        )

        stalled.release()

        assertEquals(
            CalendarScreenQueryState.Loaded(
                response = decodeMonthScreenFixture().copy(serverTime = serverTimeOfExchange(2)),
                servedFromCache = false,
                isFetching = false,
            ),
            refreshing.awaitLatest {
                it is CalendarScreenQueryState.Loaded && !it.servedFromCache && !it.isFetching
            },
        )
        assertEquals(2, fixture.exchanges)
    }

    @Test
    fun aFailureWithNothingLoadedReportsItWithNoScreenBehindIt() = runTest {
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                respondJson("{}", status = HttpStatusCode.NotFound)
            }

        val observation =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))

        val failure =
            reportedObservationFailure(
                observation.awaitLatest { it is CalendarScreenQueryState.Failed }
            )
        assertNull(failure.lastLoadedResponse, "A screen was invented behind a first failure.")
        assertEquals(false, failure.isFetching)
        assertEquals(
            HttpStatusCode.NotFound.value,
            assertIs<NonSuccessHttpStatusException>(failure.error).statusCode,
        )
    }

    @Test
    fun aFailedRefreshReportsTheFailureWithoutBlankingTheScreenBehindIt() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(
                backgroundScope,
                testScheduler,
                stalenessWindow = STALENESS_WINDOW,
            ) {
                if (++exchanges == 1) respondWithMonthScreenFixture()
                else respondJson("{}", status = HttpStatusCode.NotFound)
            }
        val first = backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        first.awaitLatest { it is CalendarScreenQueryState.Loaded }
        first.stopCollecting()

        testScheduler.advanceTimeBy(STALENESS_WINDOW)
        val refreshing =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))

        val failure =
            reportedObservationFailure(
                refreshing.awaitLatest { it is CalendarScreenQueryState.Failed }
            )
        // What a caller decides between an error screen and an error banner over live content by.
        assertEquals(decodeMonthScreenFixture(), failure.lastLoadedResponse)
        assertEquals(false, failure.isFetching)
        assertEquals(2, fixture.exchanges)
    }

    @Test
    fun cancellingTheCollectorStopsDeliveryAndNeverReportsAFailure() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondJson("{}", status = HttpStatusCode.NotFound)
            }
        val abandoning =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        runCurrent()
        val deliveredBeforeCancelling = abandoning.states.size
        abandoning.stopCollecting()
        runCurrent()

        // A second collector stays attached across the same held exchange. Without it, "nothing was
        // delivered" would pass just as well against an exchange that never finished at all — this
        // is what proves the failure was genuinely published to the state the abandoned collector
        // had been reading.
        val staying =
            backgroundScope.recordStates(fixture.query.observeScreen(monthScreenRequest()))
        runCurrent()
        // The abandoned exchange is hosted on the layer's scope, not the collector's, so it
        // survives the cancellation and fails. None of that may reach the collector that left: a
        // caller which has moved on is never handed a failure it did not stay for.
        stalled.release()
        staying.awaitLatest { it is CalendarScreenQueryState.Failed }

        assertEquals(
            deliveredBeforeCancelling,
            abandoning.states.size,
            "A cancelled collector was still delivered to: ${abandoning.states}",
        )
        assertTrue(
            abandoning.states.none { it is CalendarScreenQueryState.Failed },
            "Cancellation was reported as a failure: ${abandoning.states}",
        )
        assertEquals(1, fixture.exchanges, "The second collector did not join the held exchange.")
    }
}

/** Long enough that a case which never advances the clock cannot cross it by accident. */
private val STALENESS_WINDOW = 5.minutes
