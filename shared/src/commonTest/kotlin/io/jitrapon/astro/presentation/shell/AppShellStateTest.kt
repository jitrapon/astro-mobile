package io.jitrapon.astro.presentation.shell

import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.CalendarScreenQueryFixture
import io.jitrapon.astro.data.calendar.CalendarScreenResponse
import io.jitrapon.astro.data.calendar.MonthViewSelection
import io.jitrapon.astro.data.calendar.NavDestination
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.Navigation
import io.jitrapon.astro.data.calendar.OpenUrlAction
import io.jitrapon.astro.data.calendar.StalledExchange
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.decodeMonthScreenFixture
import io.jitrapon.astro.data.calendar.monthScreenRequest
import io.jitrapon.astro.data.calendar.respondWithMonthScreenFixture
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.presentation.calendar.CalendarViewModel
import io.jitrapon.astro.recordStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

/**
 * Pins the rules that turn a calendar screen's renderer state into the shell both apps draw, and
 * that the path from an observed screen to those tabs holds end to end below the UI.
 *
 * The rule cases are pure projections. The last case runs the real observation stack over the
 * stubbed backend instead, because what it asserts — loading first, then the contract's tabs — is
 * the sequence each app's shell is built on, and only an observation can produce a sequence.
 */
class AppShellStateTest {

    @Test
    fun theContractFixtureProjectsToItsDestinationsAsTabsInOrder() {
        assertEquals(
            AppShellState.Tabs(FIXTURE_TABS),
            loaded(decodeMonthScreenFixture()).toAppShellState(),
        )
    }

    @Test
    fun aDestinationThatDoesNotNavigateIsNotATab() {
        val screen =
            withDestinations(
                destination("calendar", NavigateAction("calendar")),
                destination("help", OpenUrlAction("https://astro.test/help")),
                destination("month", SwitchCalendarViewAction(MonthViewSelection)),
            )

        assertEquals(
            listOf("calendar"),
            (loaded(screen).toAppShellState() as AppShellState.Tabs).tabs.map { it.destinationId },
        )
    }

    @Test
    fun aRepeatedDestinationIdKeepsItsFirstOccurrence() {
        val screen =
            withDestinations(
                destination("calendar", NavigateAction("calendar"), label = "first"),
                destination("expense", NavigateAction("expense")),
                destination("calendar", NavigateAction("elsewhere"), label = "second"),
            )

        assertEquals(
            AppShellState.Tabs(
                listOf(
                    AppShellTab("calendar", "first", ICON_TOKEN, "calendar"),
                    AppShellTab("expense", "expense", ICON_TOKEN, "expense"),
                )
            ),
            loaded(screen).toAppShellState(),
        )
    }

    @Test
    fun twoDestinationsTargetingTheSameScreenAreTwoTabs() {
        val screen =
            withDestinations(
                destination("calendar", NavigateAction("calendar")),
                destination("today", NavigateAction("calendar")),
            )

        assertEquals(
            listOf("calendar", "today"),
            (loaded(screen).toAppShellState() as AppShellState.Tabs).tabs.map { it.destinationId },
        )
    }

    @Test
    fun nothingLoadedYetIsLoading() {
        assertEquals(
            AppShellState.Loading,
            CalendarUiState(content = null, isLoading = true, failure = null).toAppShellState(),
        )
        assertEquals(
            AppShellState.Loading,
            CalendarUiState(content = null, isLoading = false, failure = null).toAppShellState(),
        )
    }

    @Test
    fun aFailureWithNothingLoadedIsFailed() {
        assertEquals(
            AppShellState.Failed(REFUSED),
            CalendarUiState(content = null, isLoading = false, failure = REFUSED).toAppShellState(),
        )
    }

    @Test
    fun tabsSurviveARefreshAndAFailedRefreshOverTheScreen() {
        val screen = decodeMonthScreenFixture()

        val overTheScreen =
            listOf(
                CalendarUiState(content = screen, isLoading = true, failure = null),
                CalendarUiState(content = screen, isLoading = false, failure = REFUSED),
                CalendarUiState(content = screen, isLoading = true, failure = REFUSED),
            )

        assertTrue(
            overTheScreen.all { it.toAppShellState() == AppShellState.Tabs(FIXTURE_TABS) },
            "A refresh over a loaded screen dropped its tabs: " +
                overTheScreen.map { it.toAppShellState() },
        )
    }

    @Test
    fun aSettledScreenWithNoRoutableDestinationsHasNowhereToGo() {
        val nothingRoutable =
            listOf(
                withDestinations(destination("help", OpenUrlAction("https://astro.test/help"))),
                withDestinations(),
            )

        nothingRoutable.forEach { screen ->
            assertEquals(
                AppShellState.NoDestinations,
                CalendarUiState(content = screen, isLoading = false, failure = null)
                    .toAppShellState(),
                "A settled screen offering nothing routable did not project to NoDestinations",
            )
        }
    }

    @Test
    fun aScreenWithNoRoutableDestinationsIsStillFailedOrLoadingWhenEitherApplies() {
        val screen = withDestinations(destination("help", OpenUrlAction("https://astro.test/help")))

        assertEquals(
            AppShellState.Failed(REFUSED),
            CalendarUiState(content = screen, isLoading = false, failure = REFUSED)
                .toAppShellState(),
        )
        assertEquals(
            AppShellState.Loading,
            CalendarUiState(content = screen, isLoading = true, failure = null).toAppShellState(),
        )
    }

    @Test
    fun anObservedScreenReachesTheShellAsLoadingAndThenItsTabs() = runTest {
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
                scope = backgroundScope,
            )

        val shell = backgroundScope.recordStates(viewModel.state.map { it.toAppShellState() })

        shell.awaitLatest { it == AppShellState.Loading }
        stalled.awaitHeld()
        stalled.release()
        shell.awaitLatest { it is AppShellState.Tabs }

        assertEquals(
            listOf(AppShellState.Loading, AppShellState.Tabs(FIXTURE_TABS)),
            shell.states.distinct(),
            "The shell did not go from loading straight to the contract's tabs: ${shell.states}",
        )
    }
}

/** The tabs the vendored month-screen fixture's destinations project to, in delivered order. */
private val FIXTURE_TABS =
    listOf(
        AppShellTab(
            destinationId = "calendar",
            label = "ปฏิทิน",
            iconToken = "icon.calendar",
            targetScreenId = "calendar",
        ),
        AppShellTab(
            destinationId = "expense",
            label = "ค่าใช้จ่าย",
            iconToken = "icon.wallet",
            targetScreenId = "expense",
        ),
    )

private val REFUSED = IllegalStateException("the backend refused the exchange")

private const val ICON_TOKEN = "icon.test"

private fun loaded(screen: CalendarScreenResponse): CalendarUiState =
    CalendarUiState(content = screen, isLoading = false, failure = null)

/** The vendored fixture with its destinations replaced by [destinations]. */
private fun withDestinations(vararg destinations: NavDestination): CalendarScreenResponse {
    val fixture = decodeMonthScreenFixture()
    return fixture.copy(
        screen = fixture.screen.copy(navigation = Navigation(destinations.toList()))
    )
}

private fun destination(
    id: String,
    action: Action,
    label: String = id,
): NavDestination = NavDestination(id = id, label = label, iconToken = ICON_TOKEN, action = action)
