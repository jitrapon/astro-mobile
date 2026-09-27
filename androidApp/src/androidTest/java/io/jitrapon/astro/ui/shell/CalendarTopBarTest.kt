package io.jitrapon.astro.ui.shell

import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.ViewSwitcher
import io.jitrapon.astro.data.calendar.ViewSwitcherOption
import io.jitrapon.astro.monthBody
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pins that the calendar screen's top bar shows the delivered title and switcher, and that choosing
 * a view travels the shell's real path — the switcher's callback, the route, and
 * [AppShellViewModel.selectCalendarView] — to the screen's dispatch.
 *
 * The screen behind the shell is scripted: dispatch records each action and, like the real view
 * model once the new request has delivered, publishes a screen with the chosen view active. What
 * the real view model does with a switch — re-pointing the request it observes — is pinned by the
 * shared `CalendarViewModel` tests on both targets; a minified test build cannot reach a backend to
 * exercise it here.
 */
class CalendarTopBarTest {

    // Hosted in the app's own activity for the same reason as the shell tests: instrumented tests
    // run against the minified release build, which has no debug-only test manifest.
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val dispatched = mutableListOf<Action>()
    private val calendarState = MutableStateFlow(loaded(activeSelection = MonthViewSelection))

    @Test
    fun theTopBarShowsTheTitleAndEachOptionWithTheActiveOneSelected() {
        showShell()

        composeRule
            .onNode(
                hasText(TITLE) and hasAnyAncestor(hasTestTag(AppShellTestTags.CALENDAR_TOP_BAR))
            )
            .assertIsDisplayed()
        OPTIONS.forEach { option ->
            composeRule
                .onNodeWithTag(AppShellTestTags.viewSwitcherOption(option.id))
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag(AppShellTestTags.viewSwitcherOption(MONTH)).assertIsSelected()
        composeRule.onNodeWithTag(AppShellTestTags.viewSwitcherOption(AGENDA)).assertIsNotSelected()
    }

    @Test
    fun choosingAnInactiveViewDispatchesItsSwitchAndShowsItActive() {
        showShell()

        composeRule.onNodeWithTag(AppShellTestTags.viewSwitcherOption(AGENDA)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Action>(SwitchCalendarViewAction(AgendaViewSelection)), dispatched)
        composeRule.onNodeWithTag(AppShellTestTags.viewSwitcherOption(AGENDA)).assertIsSelected()
        composeRule.onNodeWithTag(AppShellTestTags.viewSwitcherOption(MONTH)).assertIsNotSelected()
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
                    onCalendarViewSelected = shellViewModel::selectCalendarView,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Records [action] and, for a view switch, publishes the screen with that view active. */
    private fun dispatchScripted(action: Action): ActionEffect? {
        dispatched += action
        if (action is SwitchCalendarViewAction) {
            calendarState.value = loaded(activeSelection = action.selection)
        }
        return null
    }

    private companion object {
        const val TITLE = "เมษายน 2569"
        const val MONTH = "month"
        const val AGENDA = "agenda"

        val OPTIONS =
            listOf(
                ViewSwitcherOption(id = AGENDA, label = "Agenda", selection = AgendaViewSelection),
                ViewSwitcherOption(id = MONTH, label = "Month", selection = MonthViewSelection),
            )

        /** A loaded calendar screen with one tab onto itself and [activeSelection] active. */
        fun loaded(activeSelection: CalendarViewSelection): CalendarUiState =
            CalendarUiState(
                content =
                    calendarScreenResponse(
                        body = monthBody(),
                        title = TITLE,
                        destinations =
                            listOf(
                                NavDestination(
                                    id = CALENDAR_SCREEN_ID,
                                    label = "ปฏิทิน",
                                    action = NavigateAction(CALENDAR_SCREEN_ID),
                                )
                            ),
                        viewSwitcher = ViewSwitcher(activeSelection, OPTIONS),
                    ),
                isLoading = false,
                failure = null,
            )
    }
}
