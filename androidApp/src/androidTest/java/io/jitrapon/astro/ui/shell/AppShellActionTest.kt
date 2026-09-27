package io.jitrapon.astro.ui.shell

import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.jitrapon.astro.CALENDAR_SCREEN_ID
import io.jitrapon.astro.calendarScreenResponse
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.AgendaViewSelection
import io.jitrapon.astro.data.calendar.CalendarViewSelection
import io.jitrapon.astro.data.calendar.MonthViewSelection
import io.jitrapon.astro.data.calendar.NavDestination
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.data.calendar.OpenUrlAction
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.ViewSwitcher
import io.jitrapon.astro.data.calendar.ViewSwitcherOption
import io.jitrapon.astro.monthBody
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.action.toActionEffect
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pins that a tab carrying each of the contract's five action types produces its user-visible
 * outcome when tapped, through the shell's real path: the tab's callback, the route,
 * [AppShellViewModel.selectTab], the screen's dispatch, and the shell carrying out the effect.
 *
 * Only the screen behind the shell is scripted — a minified test build can reach no backend — and
 * its dispatch answers with the shared `toActionEffect` mapping the real view model uses, so no
 * effect is injected: each one is produced from the tapped tab's delivered action.
 */
class AppShellActionTest {

    // Hosted in the app's own activity for the same reason as the shell tests: instrumented tests
    // run against the minified release build, which has no debug-only test manifest.
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val openedUrls = mutableListOf<String>()
    private val calendarState = MutableStateFlow(loaded(activeSelection = MonthViewSelection))

    @Test
    fun aNavigatingTabShowsTheScreenItTargets() {
        showShell()

        tapTab(EXPENSE)

        composeRule.onNodeWithTag(AppShellTestTags.tab(EXPENSE)).assertIsSelected()
        composeRule
            .onNodeWithTag(AppShellTestTags.destinationPlaceholder(EXPENSE))
            .assertIsDisplayed()
    }

    @Test
    fun anOpenUrlTabHandsItsUrlToThePlatformAndLeavesTheScreenInPlace() {
        showShell()

        tapTab(HELP)

        assertEquals(listOf(HELP_URL), openedUrls)
        composeRule.onNodeWithTag(AppShellTestTags.tab(CALENDAR_SCREEN_ID)).assertIsSelected()
        composeRule.onNodeWithTag(AppShellTestTags.CALENDAR_TOP_BAR).assertIsDisplayed()
    }

    @Test
    fun anOpenEventDetailTabOpensTheEventSurfaceForItsEvent() {
        showShell()

        tapTab(EVENT)

        composeRule
            .onNodeWithTag(AppShellTestTags.EVENT_SURFACE)
            .assertTextContains(EVENT_ID, substring = true)
    }

    @Test
    fun aPresentModalTabOpensTheEventSurfaceForItsEvents() {
        showShell()

        tapTab(OVERFLOW)

        composeRule
            .onNodeWithTag(AppShellTestTags.EVENT_SURFACE)
            .assertTextContains(OVERFLOW_EVENT_IDS.joinToString(", "), substring = true)
    }

    @Test
    fun aSwitchViewTabChangesTheCalendarView() {
        showShell()

        tapTab(AGENDA)

        composeRule.onNodeWithTag(AppShellTestTags.viewSwitcherOption(AGENDA)).assertIsSelected()
        composeRule.onNodeWithTag(AppShellTestTags.tab(CALENDAR_SCREEN_ID)).assertIsSelected()
    }

    private fun tapTab(destinationId: String) {
        composeRule.onNodeWithTag(AppShellTestTags.tab(destinationId)).performClick()
        composeRule.waitForIdle()
    }

    private fun showShell() {
        val shellViewModel = AppShellViewModel {
            CalendarScreenHandle(state = calendarState, dispatch = ::dispatchScripted)
        }
        // MainActivity composes the live shell in `onCreate`, and the rule refuses to set content
        // over an activity that already has some. Detach it first so only this shell renders.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        }
        composeRule.setContent {
            AstroTheme {
                AppShellRoute(
                    shellState = shellViewModel.shellState,
                    calendarState = shellViewModel.calendarState,
                    interactions =
                        AppShellInteractions(
                            onTabSelected = shellViewModel::selectTab,
                            onCalendarViewSelected = shellViewModel::selectCalendarView,
                            onOpenExternalUrl = { openedUrls += it },
                        ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Answers [action] the way the real view model does: a view switch re-points the screen — here,
     * by publishing it with the chosen view active — and every action maps to its effect through
     * the shared [toActionEffect].
     */
    private fun dispatchScripted(action: Action): ActionEffect? {
        if (action is SwitchCalendarViewAction) {
            calendarState.value = loaded(activeSelection = action.selection)
        }
        return action.toActionEffect()
    }

    private companion object {
        const val EXPENSE = "expense"
        const val HELP = "help"
        const val EVENT = "event"
        const val OVERFLOW = "overflow"
        const val AGENDA = "agenda"
        const val MONTH = "month"
        const val HELP_URL = "https://astro.test/help"
        const val EVENT_ID = "e1"
        val OVERFLOW_EVENT_IDS = listOf("e1", "e2")

        /** One destination per contract action type, plus a second navigating one to move to. */
        val DESTINATIONS =
            listOf(
                destination(CALENDAR_SCREEN_ID, NavigateAction(CALENDAR_SCREEN_ID)),
                destination(EXPENSE, NavigateAction(EXPENSE)),
                destination(HELP, OpenUrlAction(HELP_URL)),
                destination(EVENT, OpenEventDetailAction(EVENT_ID)),
                destination(OVERFLOW, PresentModalAction(OVERFLOW_EVENT_IDS)),
                destination(AGENDA, SwitchCalendarViewAction(AgendaViewSelection)),
            )

        val OPTIONS =
            listOf(
                ViewSwitcherOption(id = AGENDA, label = "Agenda", selection = AgendaViewSelection),
                ViewSwitcherOption(id = MONTH, label = "Month", selection = MonthViewSelection),
            )

        fun destination(id: String, action: Action) =
            NavDestination(id = id, label = id, action = action)

        /** A loaded calendar screen carrying [DESTINATIONS], with [activeSelection] active. */
        fun loaded(activeSelection: CalendarViewSelection): CalendarUiState =
            CalendarUiState(
                content =
                    calendarScreenResponse(
                        body = monthBody(),
                        destinations = DESTINATIONS,
                        viewSwitcher = ViewSwitcher(activeSelection, OPTIONS),
                    ),
                isLoading = false,
                failure = null,
            )
    }
}
