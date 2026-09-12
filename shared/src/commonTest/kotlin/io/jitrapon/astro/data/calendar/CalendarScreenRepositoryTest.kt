package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.data.network.NonSuccessHttpStatusException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

/**
 * Holds [CalendarScreenRepository] to being a forwarding boundary and nothing more.
 *
 * The repository exists so nothing above it reaches past it into the API client. It forwards two
 * different things, and the cases below separate them: a one-shot fetch goes straight to the API
 * client and is answered by an exchange every time, while observation, refresh and invalidation go
 * to [CalendarScreenQuery] and are answered by whatever that layer has remembered.
 *
 * Each observation case is written so that forwarding to the wrong one of those fails it. A screen
 * served without a second exchange, a refresh that exchanges inside the staleness window, and an
 * invalidation that a later observation notices are all behaviour the API client alone cannot
 * produce — so a repository that quietly called `fetchCalendarScreen` underneath would be caught
 * rather than passing on identical-looking screens.
 *
 * What none of them re-test is the query itself. How many exchanges concurrent observers cause and
 * what a failure does to a loaded screen are pinned next door, against the query directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarScreenRepositoryTest {

    @Test
    fun forwardsTheDeliveredScreenUnchanged() = runTest {
        val fixture = CalendarScreenQueryFixture(backgroundScope, testScheduler)

        val outcome = fixture.calendarScreenRepository.fetchCalendarScreen(monthScreenRequest())

        // Equal to what the fixture decodes to independently: the repository adds no field, drops
        // none, and rewrites none on the way through.
        assertEquals(decodeMonthScreenFixture(), deliveredScreen(outcome))
        assertEquals(1, fixture.exchanges)
    }

    @Test
    fun forwardsAReportedFailureUnchanged() = runTest {
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                respondJson("{}", status = HttpStatusCode.NotFound)
            }

        val outcome = fixture.calendarScreenRepository.fetchCalendarScreen(monthScreenRequest())

        // Not repaired, not retried, not reinterpreted — the same failure the client reported,
        // still carrying the status a caller would branch on.
        assertEquals(
            HttpStatusCode.NotFound.value,
            reportedFailure<NonSuccessHttpStatusException>(outcome).statusCode,
        )
        assertEquals(1, fixture.exchanges)
    }

    @Test
    fun answersEachOfTwoSuccessiveRequestsWithItsOwnScreen() = runTest {
        // The stub echoes the requested zone back in the envelope, so a screen belonging to the
        // wrong request is visible in the response rather than only in the request log.
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
        val repository = fixture.calendarScreenRepository

        val bangkok = repository.fetchCalendarScreen(monthScreenRequest())
        val zurich =
            repository.fetchCalendarScreen(monthScreenRequest().copy(timeZone = ZURICH_TIME_ZONE))

        assertEquals(BANGKOK_TIME_ZONE, deliveredScreen(bangkok).timeZone)
        assertEquals(ZURICH_TIME_ZONE, deliveredScreen(zurich).timeZone)
        assertEquals(2, fixture.exchanges, "A second request was answered without being sent.")
    }

    @Test
    fun observingGoesThroughTheQueryRatherThanRepeatingTheFetch() = runTest {
        val fixture = CalendarScreenQueryFixture(backgroundScope, testScheduler)
        val repository = fixture.calendarScreenRepository
        val request = monthScreenRequest()
        val first = backgroundScope.recordStates(repository.observeCalendarScreen(request))
        first.awaitLatest { it is CalendarScreenQueryState.Loaded }

        val second = backgroundScope.recordStates(repository.observeCalendarScreen(request))

        // Served from what the query remembered. A repository forwarding to the API client instead
        // would deliver an equal screen, so the exchange count is what tells the two apart.
        val remembered = second.awaitLatest {
            it is CalendarScreenQueryState.Loaded && it.servedFromCache
        }
        assertEquals(decodeMonthScreenFixture(), loadedResponse(remembered))
        assertEquals(1, fixture.exchanges, "A remembered screen was fetched a second time.")
    }

    @Test
    fun refetchingExchangesEvenWhileTheRememberedScreenIsStillFresh() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                respondWithServerTime(serverTimeOfExchange(++exchanges))
            }
        val repository = fixture.calendarScreenRepository
        val request = monthScreenRequest()
        val observation = backgroundScope.recordStates(repository.observeCalendarScreen(request))
        observation.awaitLatest { it is CalendarScreenQueryState.Loaded }

        // The clock is never advanced, so what is remembered is still comfortably fresh — the one
        // condition under which an observation would decline to exchange at all.
        repository.refetchCalendarScreen(request)

        assertEquals(
            serverTimeOfExchange(2),
            loadedResponse(
                    observation.awaitLatest {
                        it is CalendarScreenQueryState.Loaded &&
                            it.response.serverTime == serverTimeOfExchange(2)
                    }
                )
                .serverTime,
        )
        assertEquals(2, fixture.exchanges, "An explicit refresh was answered from memory.")
    }

    @Test
    fun invalidatingDropsTheMatchingScreensAndLeavesTheRestRemembered() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) { request ->
                respondWithZoneAndServerTime(
                    zone = request.url.parameters["tz"].orEmpty(),
                    serverTime = serverTimeOfExchange(++exchanges),
                )
            }
        val repository = fixture.calendarScreenRepository
        val bangkok = monthScreenRequest()
        val zurich = monthScreenRequest().copy(timeZone = ZURICH_TIME_ZONE)
        // Loaded one at a time so the exchange numbering below is an assertion rather than a race.
        backgroundScope.recordStates(repository.observeCalendarScreen(bangkok)).awaitLatest {
            it is CalendarScreenQueryState.Loaded
        }
        backgroundScope.recordStates(repository.observeCalendarScreen(zurich)).awaitLatest {
            it is CalendarScreenQueryState.Loaded
        }

        repository.invalidateCalendarScreens { it.timeZone == BANGKOK_TIME_ZONE }

        val reobservedBangkok =
            backgroundScope.recordStates(repository.observeCalendarScreen(bangkok))
        assertEquals(
            BANGKOK_TIME_ZONE,
            loadedResponse(
                    reobservedBangkok.awaitLatest {
                        it is CalendarScreenQueryState.Loaded &&
                            it.response.serverTime ==
                                serverTimeOfExchange(EXCHANGE_AFTER_INVALIDATION)
                    }
                )
                .timeZone,
        )
        val reobservedZurich =
            backgroundScope.recordStates(repository.observeCalendarScreen(zurich))
        val untouched = reobservedZurich.awaitLatest {
            it is CalendarScreenQueryState.Loaded && it.servedFromCache
        }
        assertEquals(serverTimeOfExchange(2), loadedResponse(untouched).serverTime)
        assertEquals(
            EXCHANGE_AFTER_INVALIDATION,
            fixture.exchanges,
            "An invalidation dropped a screen whose request it did not match.",
        )
    }
}

/**
 * The exchange an invalidation causes: two screens are loaded first, so the refetch of the one that
 * matched is the third.
 */
private const val EXCHANGE_AFTER_INVALIDATION = 3
