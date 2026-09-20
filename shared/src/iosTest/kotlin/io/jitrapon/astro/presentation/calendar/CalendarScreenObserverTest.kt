package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.CalendarScreenQueryFixture
import io.jitrapon.astro.data.calendar.CalendarScreenQueryState
import io.jitrapon.astro.data.calendar.StalledExchange
import io.jitrapon.astro.data.calendar.decodeMonthScreenFixture
import io.jitrapon.astro.data.calendar.monthScreenRequest
import io.jitrapon.astro.data.calendar.respondWithMonthScreenFixture
import io.jitrapon.astro.data.calendar.respondWithServerTime
import io.jitrapon.astro.data.calendar.serverTimeOfExchange
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.toAppShellState
import io.jitrapon.astro.recordStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Pins the adapter Swift subscribes through, on the target Swift subscribes from.
 *
 * The iOS app build only proves a Swift call site compiles against this class; it says nothing
 * about whether states keep arriving, whether cancelling actually stops them, or whether a
 * cancelled subscription leaves a coroutine behind. Those are behaviours of the Kotlin side, so
 * they are asserted here, over the real observation stack with only the backend stubbed.
 *
 * Every case delivers on a scope running on the test scheduler rather than on the main thread: the
 * test runner blocks the main thread while a test runs, so a main-queue dispatch would never land.
 * Which thread delivery happens on is the graph's choice, not this class's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarScreenObserverTest {

    @Test
    fun theCallbackHearsTheLoadBeforeTheScreenAndThenTheScreen() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondWithMonthScreenFixture()
            }
        val observer = CalendarScreenObserver(fixture.calendarScreenRepository, deliveryScope())
        val heard = HeardStates()

        observer.observe(monthScreenRequest(), heard::record)

        assertEquals(
            CalendarUiState(content = null, isLoading = true, failure = null),
            heard.awaitLatest { it.isLoading },
        )
        stalled.release()
        val screen = heard.awaitLatest { it.content != null }

        assertEquals(
            CalendarUiState(
                content = decodeMonthScreenFixture(),
                isLoading = false,
                failure = null,
            ),
            screen,
        )
        assertTrue(
            heard.states.indexOfFirst { it.isLoading } < heard.states.indexOf(screen),
            "The screen reached the callback without the load before it: ${heard.states}",
        )
    }

    @Test
    fun theDeliveredStatesProjectToALoadingShellAndThenTheContractTabs() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondWithMonthScreenFixture()
            }
        val observer = CalendarScreenObserver(fixture.calendarScreenRepository, deliveryScope())
        val heard = HeardStates()

        observer.observe(monthScreenRequest(), heard::record)

        heard.awaitLatest { it.isLoading }
        stalled.release()
        heard.awaitLatest { it.content != null }

        val shell = heard.states.map { it.toAppShellState() }.distinct()
        assertEquals(
            AppShellState.Loading,
            shell.first(),
            "The shell did not start loading: $shell",
        )
        assertEquals(
            listOf("calendar", "expense"),
            (shell.last() as AppShellState.Tabs).tabs.map { it.destinationId },
            "The last delivered state did not project to the contract's tabs: $shell",
        )
        assertEquals(2, shell.size, "The shell passed through a state other than loading: $shell")
    }

    @Test
    fun aCancelledSubscriptionHearsNothingLaterAndLeavesNothingRunning() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                respondWithServerTime(serverTimeOfExchange(++exchanges))
            }
        val delivery = deliveryScope()
        val observer = CalendarScreenObserver(fixture.calendarScreenRepository, delivery)
        val heard = HeardStates()
        val subscription = observer.observe(monthScreenRequest(), heard::record)
        heard.awaitLatest { it.content?.serverTime == serverTimeOfExchange(1) }

        subscription.cancel()
        advanceUntilIdle()

        assertTrue(
            delivery.coroutineContext.job.children.none(),
            "Cancelling the subscription left its coroutines running.",
        )

        // A newer screen is published to the same request after the cancellation. Waiting for it to
        // land through another observer is what makes the silence below mean something.
        fixture.calendarScreenRepository.refetchCalendarScreen(monthScreenRequest())
        awaitPublishedServerTime(fixture, serverTimeOfExchange(2))
        advanceUntilIdle()

        assertTrue(
            heard.states.none { it.content?.serverTime == serverTimeOfExchange(2) },
            "A cancelled subscription was still delivered a later screen: ${heard.states}",
        )
    }

    @Test
    fun cancellingMidFlightDeliversNeitherTheScreenNorAFailure() = runTest {
        val stalled = StalledExchange()
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                stalled.hold()
                respondWithMonthScreenFixture()
            }
        val observer = CalendarScreenObserver(fixture.calendarScreenRepository, deliveryScope())
        val heard = HeardStates()
        val subscription = observer.observe(monthScreenRequest(), heard::record)
        heard.awaitLatest { it.isLoading }
        stalled.awaitHeld()

        subscription.cancel()
        stalled.release()

        // The exchange is not cancelled with the subscription that was waiting on it, so it still
        // lands. Waiting for that is what separates "nothing was delivered" from "the test ended
        // before anything could have been".
        backgroundScope
            .recordStates(
                fixture.calendarScreenRepository.observeCalendarScreen(monthScreenRequest())
            )
            .awaitLatest { it is CalendarScreenQueryState.Loaded }
        advanceUntilIdle()

        assertTrue(
            heard.states.none { it.content != null || it.failure != null },
            "A cancelled subscription heard how its exchange ended: ${heard.states}",
        )
        assertEquals(1, fixture.exchanges, "The abandoned exchange was started a second time.")
    }

    @Test
    fun cancellingTwiceIsHarmlessAndLeavesOtherSubscriptionsListening() = runTest {
        var exchanges = 0
        val fixture =
            CalendarScreenQueryFixture(backgroundScope, testScheduler) {
                respondWithServerTime(serverTimeOfExchange(++exchanges))
            }
        val delivery = deliveryScope()
        val observer = CalendarScreenObserver(fixture.calendarScreenRepository, delivery)
        val cancelled = HeardStates()
        val listening = HeardStates()
        val subscription = observer.observe(monthScreenRequest(), cancelled::record)
        observer.observe(monthScreenRequest(), listening::record)
        listening.awaitLatest { it.content != null }

        subscription.cancel()
        subscription.cancel()
        fixture.calendarScreenRepository.refetchCalendarScreen(monthScreenRequest())

        listening.awaitLatest { it.content?.serverTime == serverTimeOfExchange(2) }
        assertTrue(delivery.isActive, "Cancelling one subscription ended the delivery scope.")
    }

    /** Waits until an observation of the month request is showing the screen served at [time]. */
    private suspend fun TestScope.awaitPublishedServerTime(
        fixture: CalendarScreenQueryFixture,
        time: String,
    ) {
        backgroundScope
            .recordStates(
                fixture.calendarScreenRepository.observeCalendarScreen(monthScreenRequest())
            )
            .awaitLatest { (it as? CalendarScreenQueryState.Loaded)?.response?.serverTime == time }
    }
}

/**
 * What one callback was called with, in order, and a way to wait for a particular state to arrive.
 *
 * Waiting rather than draining the scheduler, because an exchange travels through Ktor's engine on
 * a real dispatcher: an idle test scheduler says nothing about whether the backend has answered.
 */
private class HeardStates {

    private val heard = MutableStateFlow<List<CalendarUiState>>(emptyList())

    val states: List<CalendarUiState>
        get() = heard.value

    fun record(state: CalendarUiState) {
        heard.update { it + state }
    }

    suspend fun awaitLatest(predicate: (CalendarUiState) -> Boolean): CalendarUiState =
        heard.mapNotNull { it.lastOrNull() }.first(predicate)
}

/**
 * The scope standing in for the graph's main-thread delivery scope.
 *
 * Its job is a supervisor child of the background scope, so nothing is left running when a case
 * ends. Its dispatcher is deliberately *not* the background scope's: work dispatched from
 * `backgroundScope` is invisible to `advanceUntilIdle`, which stops as soon as only background work
 * remains. Delivering there would leave a cancellation unprocessed and a late delivery unrun, and
 * every "nothing further arrived" assertion would then pass without anything having been given the
 * chance to arrive.
 */
private fun TestScope.deliveryScope(): CoroutineScope =
    CoroutineScope(
        StandardTestDispatcher(testScheduler) + SupervisorJob(backgroundScope.coroutineContext.job)
    )
