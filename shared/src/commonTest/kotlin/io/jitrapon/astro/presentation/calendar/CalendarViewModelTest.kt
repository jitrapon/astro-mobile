package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.CalendarScreenQueryFixture
import io.jitrapon.astro.data.calendar.CalendarScreenQueryState
import io.jitrapon.astro.data.calendar.StalledExchange
import io.jitrapon.astro.data.calendar.decodeMonthScreenFixture
import io.jitrapon.astro.data.calendar.monthScreenRequest
import io.jitrapon.astro.data.calendar.respondWithMonthScreenFixture
import io.jitrapon.astro.recordStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Pins the two things a view model is answerable for: that every state an observation can publish
 * arrives at a renderer as something it can paint, and that the scope it was handed is what ends
 * the collection.
 *
 * The mapping cases are exhaustive over [CalendarScreenQueryState] and need no coroutines at all —
 * a projection is a projection. The cases below them run the real stack instead, because what they
 * assert is about *time*: a load that has not answered yet, and a screen that arrives after the
 * scope it was being collected for has gone away. Neither is observable in a mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @Test
    fun aScreenIsPaintedTheSameWhicheverWayItWasServed() {
        val screen = decodeMonthScreenFixture()

        val remembered =
            CalendarScreenQueryState.Loaded(screen, servedFromCache = true, isFetching = false)
        val exchanged =
            CalendarScreenQueryState.Loaded(screen, servedFromCache = false, isFetching = false)

        assertEquals(
            CalendarUiState(content = screen, isLoading = false, failure = null),
            remembered.toCalendarUiState(),
        )
        assertEquals(
            remembered.toCalendarUiState(),
            exchanged.toCalendarUiState(),
            "Where a screen came from reached the renderer, which could then paint it differently.",
        )
    }

    @Test
    fun aFailureKeepsPaintingTheScreenItLandedOver() {
        val screen = decodeMonthScreenFixture()
        val refused = IllegalStateException("the backend refused the exchange")

        assertEquals(
            CalendarUiState(content = screen, isLoading = false, failure = refused),
            CalendarScreenQueryState.Failed(
                    refused,
                    lastLoadedResponse = screen,
                    isFetching = false,
                )
                .toCalendarUiState(),
        )
        assertEquals(
            CalendarUiState(content = null, isLoading = false, failure = refused),
            CalendarScreenQueryState.Failed(refused, lastLoadedResponse = null, isFetching = false)
                .toCalendarUiState(),
        )
    }

    @Test
    fun theInFlightFlagReachesTheRendererFromEveryCase() {
        val screen = decodeMonthScreenFixture()
        val refused = IllegalStateException("the backend refused the exchange")

        val loading =
            listOf(
                CalendarScreenQueryState.Pending(isFetching = true),
                CalendarScreenQueryState.Loaded(screen, servedFromCache = true, isFetching = true),
                CalendarScreenQueryState.Failed(refused, lastLoadedResponse = screen, true),
            )

        assertTrue(
            loading.all { it.toCalendarUiState().isLoading },
            "A case dropped the in-flight flag: ${loading.map { it.toCalendarUiState() }}",
        )
    }

    @Test
    fun theStateShowsTheLoadBeforeTheScreenAndThenTheScreen() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondWithMonthScreenFixture()
            }
        val viewModel =
            CalendarViewModel(
                calendarScreenRepository = fixture.calendarScreenRepository,
                request = monthScreenRequest(),
                scope = presentationScope(),
            )

        val painted = backgroundScope.recordStates(viewModel.state)

        assertEquals(
            CalendarUiState(content = null, isLoading = true, failure = null),
            painted.awaitLatest { it.isLoading },
        )
        stalled.release()
        assertEquals(
            CalendarUiState(
                content = decodeMonthScreenFixture(),
                isLoading = false,
                failure = null,
            ),
            painted.awaitLatest { it.content != null },
        )
    }

    @Test
    fun aScreenThatArrivesAfterTheScopeEndedReachesNobody() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondWithMonthScreenFixture()
            }
        val presenting = presentationScope()
        val viewModel =
            CalendarViewModel(
                calendarScreenRepository = fixture.calendarScreenRepository,
                request = monthScreenRequest(),
                scope = presenting,
            )
        val painted = backgroundScope.recordStates(viewModel.state)
        painted.awaitLatest { it.isLoading }
        stalled.awaitHeld()

        presenting.cancel()
        stalled.release()

        // The exchange is deliberately not cancelled with the screen that was waiting on it, so it
        // still lands and is still published to whoever observes that request. Waiting for it here
        // is what makes the assertion below evidence: without it, "no screen was painted" would
        // hold just as well of a test that ended before the backend answered.
        backgroundScope
            .recordStates(
                fixture.calendarScreenRepository.observeCalendarScreen(monthScreenRequest())
            )
            .awaitLatest { it is CalendarScreenQueryState.Loaded }
        advanceUntilIdle()

        assertTrue(
            painted.states.none { it.content != null },
            "A screen was painted for a scope that had already ended: ${painted.states}",
        )
        assertEquals(1, fixture.exchanges, "The abandoned exchange was started a second time.")
    }
}

/**
 * A scope standing in for whatever presents a screen — the one a view model is handed and never
 * owns.
 *
 * A child of the test's background scope, so a case that never cancels it still leaves nothing
 * running, and cancelling it inside a case is exactly the screen going away.
 */
private fun TestScope.presentationScope(): CoroutineScope =
    CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
