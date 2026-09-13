package io.jitrapon.astro.data.calendar

import io.jitrapon.astro.recordStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the two ways a screen is brought up to date on purpose rather than by ageing: an explicit
 * refresh, and an invalidation that declares what is remembered to be wrong.
 *
 * What these cases are really about is *which answer is still the right one*. An exchange is never
 * cancelled, so one that left before an invalidation can come back after it, carrying a screen the
 * server has already disowned — and ordering alone cannot tell that apart from a legitimate answer,
 * because the obsolete one may well acquire every lock first. So the cases here hold an exchange
 * open across an invalidation and then assert on what it was and was not allowed to do.
 *
 * Each exchange answers with its own server clock, which is what makes "the screen from the second
 * exchange was published" an assertion rather than an inference — a screen that merely *looks*
 * right is indistinguishable from the one it replaced without it. Requests are therefore loaded one
 * at a time wherever the numbering is asserted on: two observations started together race for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarScreenQueryRefreshTest {

    @Test
    fun refetchingExchangesEvenWhileTheRememberedScreenIsStillFresh() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                respondWithServerTime(serverTimeOfExchange(++exchanges))
            }
        val request = monthScreenRequest()
        val observation = backgroundScope.recordStates(fixture.query.observeScreen(request))
        observation.awaitLatest { it is CalendarScreenQueryState.Loaded }

        // The clock is never advanced, so what is remembered is still comfortably inside its
        // window — the one condition under which an observation would decline to exchange at all.
        fixture.query.refetchScreen(request)

        assertEquals(
            CalendarScreenQueryState.Loaded(
                response = decodeMonthScreenFixture().copy(serverTime = serverTimeOfExchange(2)),
                servedFromCache = false,
                isFetching = false,
            ),
            observation.awaitLatest { screenShowing(it)?.serverTime == serverTimeOfExchange(2) },
        )
        assertEquals(
            2,
            fixture.exchanges,
            "An explicit refresh was answered from the cache instead of the network.",
        )
    }

    @Test
    fun aRefreshWhoseCallerIsCancelledStillSettlesTheScreenForItsObservers() = runTest {
        val stalled = StalledExchange()
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                val nth = ++exchanges
                if (nth == 2) stalled.hold()
                respondWithServerTime(serverTimeOfExchange(nth))
            }
        val request = monthScreenRequest()
        val observation = backgroundScope.recordStates(fixture.query.observeScreen(request))
        observation.awaitLatest { it is CalendarScreenQueryState.Loaded }

        // The caller that asked for the refresh goes away while its exchange is in flight — a
        // pull-to-refresh whose screen was dismissed. The observer that stays behind is the one
        // whose in-flight flag that refresh raised, so it is the one left waiting on the outcome.
        val abandonedRefresh = backgroundScope.launch { fixture.query.refetchScreen(request) }
        stalled.awaitHeld()
        abandonedRefresh.cancel()
        stalled.release()

        // A regression reports as a case that stops making progress: nothing publishes the
        // answer, so the observer keeps the first screen under a flag that never comes down.
        assertEquals(
            CalendarScreenQueryState.Loaded(
                response = decodeMonthScreenFixture().copy(serverTime = serverTimeOfExchange(2)),
                servedFromCache = false,
                isFetching = false,
            ),
            observation.awaitLatest { screenShowing(it)?.serverTime == serverTimeOfExchange(2) },
        )
    }

    @Test
    fun invalidatingDropsTheMatchingRememberedScreensAndLeavesTheRestRemembered() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) { request ->
                respondWithZoneAndServerTime(
                    zone = request.url.parameters["tz"].orEmpty(),
                    serverTime = serverTimeOfExchange(++exchanges),
                )
            }
        val bangkok = monthScreenRequest()
        val zurich = monthScreenRequest().copy(timeZone = ZURICH_TIME_ZONE)
        backgroundScope.loadOnce(fixture, bangkok)
        backgroundScope.loadOnce(fixture, zurich)

        fixture.query.invalidateScreens { it.timeZone == BANGKOK_TIME_ZONE }

        val reobservedBangkok = backgroundScope.recordStates(fixture.query.observeScreen(bangkok))
        val refreshed = reobservedBangkok.awaitLatest {
            screenShowing(it)?.serverTime == serverTimeOfExchange(EXCHANGE_AFTER_INVALIDATION)
        }
        assertEquals(BANGKOK_TIME_ZONE, loadedResponse(refreshed).timeZone)

        val reobservedZurich = backgroundScope.recordStates(fixture.query.observeScreen(zurich))
        val remembered = reobservedZurich.awaitLatest {
            it is CalendarScreenQueryState.Loaded && it.servedFromCache
        }
        assertEquals(serverTimeOfExchange(2), loadedResponse(remembered).serverTime)
        assertEquals(
            EXCHANGE_AFTER_INVALIDATION,
            fixture.exchanges,
            "An invalidation dropped a screen whose request it did not match.",
        )
    }

    @Test
    fun invalidatingRefreshesALiveSubscriberWithoutItObservingAgain() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) { request ->
                respondWithZoneAndServerTime(
                    zone = request.url.parameters["tz"].orEmpty(),
                    serverTime = serverTimeOfExchange(++exchanges),
                )
            }
        val bangkok = monthScreenRequest()
        val zurich = monthScreenRequest().copy(timeZone = ZURICH_TIME_ZONE)
        val watching = backgroundScope.recordStates(fixture.query.observeScreen(bangkok))
        watching.awaitLatest { it is CalendarScreenQueryState.Loaded }
        val unaffected = backgroundScope.recordStates(fixture.query.observeScreen(zurich))
        unaffected.awaitLatest { it is CalendarScreenQueryState.Loaded }
        val unaffectedBefore = unaffected.states

        fixture.query.invalidateScreens { it.timeZone == BANGKOK_TIME_ZONE }

        // Nothing re-observes: the collector that attached before the invalidation is the one that
        // has to see the new screen, which is the whole difference between invalidating a screen
        // and merely forgetting it.
        val refreshed = watching.awaitLatest {
            screenShowing(it)?.serverTime == serverTimeOfExchange(EXCHANGE_AFTER_INVALIDATION)
        }
        assertEquals(BANGKOK_TIME_ZONE, loadedResponse(refreshed).timeZone)
        assertEquals(false, assertIs<CalendarScreenQueryState.Loaded>(refreshed).servedFromCache)
        assertEquals(
            unaffectedBefore,
            unaffected.states,
            "A subscription the invalidation did not match was refreshed anyway.",
        )
        assertEquals(EXCHANGE_AFTER_INVALIDATION, fixture.exchanges)
    }

    @Test
    fun anAnswerFromBeforeAnInvalidationIsNeitherPublishedNorRemembered() = runTest {
        val stalled = StalledExchange()
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                val nth = ++exchanges
                // Only the refresh below is held, and holding it is what lets an invalidation
                // overtake an exchange that is already on its way back.
                if (nth == 2) stalled.hold()
                respondWithServerTime(serverTimeOfExchange(nth))
            }
        val request = monthScreenRequest()
        val observation = backgroundScope.recordStates(fixture.query.observeScreen(request))
        observation.awaitLatest { it is CalendarScreenQueryState.Loaded }

        // Driven through `refetchScreen` rather than a second observation because it suspends until
        // its exchange has been dealt with — which is what makes "the obsolete answer has come back
        // and done whatever it is going to do" something this case can wait for rather than assume.
        val obsolete = backgroundScope.launch { fixture.query.refetchScreen(request) }
        stalled.awaitHeld()

        fixture.query.invalidateScreens { it == request }
        observation.awaitLatest {
            screenShowing(it)?.serverTime == serverTimeOfExchange(EXCHANGE_AFTER_INVALIDATION)
        }

        stalled.release()
        obsolete.join()

        assertTrue(
            observation.states.none { screenShowing(it)?.serverTime == serverTimeOfExchange(2) },
            "An answer the invalidation had ruled out was published: ${observation.states}",
        )
        val reobserved = backgroundScope.recordStates(fixture.query.observeScreen(request))
        val remembered = reobserved.awaitLatest {
            it is CalendarScreenQueryState.Loaded && it.servedFromCache
        }
        assertEquals(
            serverTimeOfExchange(EXCHANGE_AFTER_INVALIDATION),
            loadedResponse(remembered).serverTime,
            "An answer the invalidation had ruled out was written back into the cache.",
        )
        assertEquals(EXCHANGE_AFTER_INVALIDATION, fixture.exchanges)
    }

    @Test
    fun aRequestArrivingAfterAnInvalidationStartsItsOwnExchange() = runTest {
        val stalled = StalledExchange()
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                val nth = ++exchanges
                if (nth == 1) stalled.hold()
                respondWithServerTime(serverTimeOfExchange(nth))
            }
        val request = monthScreenRequest()
        val abandoned = backgroundScope.recordStates(fixture.query.observeScreen(request))
        stalled.awaitHeld()
        abandoned.stopCollecting()
        runCurrent()

        fixture.query.invalidateScreens { it == request }

        val arriving = backgroundScope.recordStates(fixture.query.observeScreen(request))
        // Joining the held exchange would leave this observation waiting on an answer that is never
        // published, so a regression here reports as a case that stops making progress rather than
        // one that asserts the wrong value.
        val loaded = arriving.awaitLatest {
            screenShowing(it)?.serverTime == serverTimeOfExchange(2)
        }
        assertEquals(false, assertIs<CalendarScreenQueryState.Loaded>(loaded).servedFromCache)
        assertEquals(2, fixture.exchanges)
    }

    @Test
    fun concurrentObservationRefreshAndInvalidationLeaveTheTablesIntact() = runTest {
        val fixture = CalendarScreenQueryFixture(backgroundScope, testScheduler)
        val request = monthScreenRequest()

        val contendingCalls =
            listOf<suspend () -> Unit>(
                {
                    fixture.query.observeScreen(request).first {
                        it is CalendarScreenQueryState.Loaded
                    }
                },
                { fixture.query.refetchScreen(request) },
                { fixture.query.invalidateScreens { it == request } },
            )
        val churn =
            List(CONCURRENT_CALLERS) { nth ->
                backgroundScope.launch { contendingCalls[nth % contendingCalls.size]() }
            }
        churn.joinAll()

        // The claim is that the layer settles at all. A table mutated from two threads at once
        // raises a catchable exception on the JVM and corrupts memory on Kotlin/Native, where it
        // surfaces as a crash or a hang far from the write that caused it — so this case is only
        // evidence once it has run on both targets.
        val settled = backgroundScope.recordStates(fixture.query.observeScreen(request))
        assertEquals(
            decodeMonthScreenFixture(),
            loadedResponse(settled.awaitLatest { it is CalendarScreenQueryState.Loaded }),
        )
    }
}

/**
 * Which exchange an invalidation causes, in the cases that have caused exactly two before they
 * invalidate.
 *
 * It doubles as the total those cases must end on: an invalidation that is not the last exchange
 * caused one nobody asked for.
 */
private const val EXCHANGE_AFTER_INVALIDATION = 3

/** Enough simultaneous callers to interleave, few enough to stay quick on a simulator. */
private const val CONCURRENT_CALLERS = 24

/**
 * Observes [request] until its screen has loaded, then stops collecting.
 *
 * Leaves the screen remembered and no collector attached, which is the starting position for every
 * case that asks what an invalidation does to what is *not* being watched.
 */
private suspend fun CoroutineScope.loadOnce(
    fixture: CalendarScreenQueryFixture,
    request: CalendarScreenRequest,
) {
    val observation = recordStates(fixture.query.observeScreen(request))
    observation.awaitLatest { it is CalendarScreenQueryState.Loaded }
    observation.stopCollecting()
}

/** The screen [state] is showing, or `null` when it is not showing one. */
private fun screenShowing(state: CalendarScreenQueryState): CalendarScreenResponse? =
    (state as? CalendarScreenQueryState.Loaded)?.response
