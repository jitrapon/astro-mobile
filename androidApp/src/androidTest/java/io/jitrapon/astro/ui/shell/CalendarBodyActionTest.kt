package io.jitrapon.astro.ui.shell

import android.view.ViewGroup
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.jitrapon.astro.CALENDAR_SCREEN_ID
import io.jitrapon.astro.allDayEvent
import io.jitrapon.astro.calendarScreenResponse
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.MonthAllDayBarPresentation
import io.jitrapon.astro.data.calendar.NavDestination
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.monthBody
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.action.toActionEffect
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.ui.component.CalendarComponentTestTags
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pins that the affordances on the calendar body dispatch the actions the contract never attaches
 * to them — an event tap its open-event-detail, the month overflow its present-modal over the ids
 * it hides — and that each reaches the screen's dispatch through the shell and opens the event
 * surface.
 *
 * The screen behind the shell is scripted, as in the other shell tests, and answers through the
 * shared `toActionEffect` mapping the real view model uses.
 */
class CalendarBodyActionTest {

    // Hosted in the app's own activity for the same reason as the shell tests: instrumented tests
    // run against the minified release build, which has no debug-only test manifest.
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val dispatched = mutableListOf<Action>()

    @Test
    fun tappingAnEventDispatchesOpenEventDetailForThatEvent() {
        showShell()

        composeRule.onNodeWithText(EVENT_TITLES.first()).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Action>(OpenEventDetailAction(EVENT_IDS.first())), dispatched)
        composeRule
            .onNodeWithTag(AppShellTestTags.EVENT_SURFACE)
            .assertTextContains(EVENT_IDS.first(), substring = true)
    }

    @Test
    fun tappingTheMonthOverflowPresentsTheEventsItHides() {
        showShell()
        val hiddenIds = EVENT_IDS.drop(VISIBLE_MONTH_EVENTS)

        composeRule.onNodeWithTag(CalendarComponentTestTags.MONTH_OVERFLOW).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Action>(PresentModalAction(hiddenIds)), dispatched)
        composeRule
            .onNodeWithTag(AppShellTestTags.EVENT_SURFACE)
            .assertTextContains(hiddenIds.joinToString(", "), substring = true)
    }

    @Test
    fun theMonthOverflowIsAFullSizeTouchTarget() {
        showShell()

        composeRule
            .onNodeWithTag(CalendarComponentTestTags.MONTH_OVERFLOW)
            .assertHeightIsAtLeast(MINIMUM_TOUCH_TARGET)
    }

    private fun showShell() {
        val calendarState = MutableStateFlow(LOADED)
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
                            dispatch = shellViewModel::dispatch,
                            openExternalUrl = {},
                        ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Records [action] and answers it through the shared mapping, as the real view model does. */
    private fun dispatchScripted(action: Action): ActionEffect? {
        dispatched += action
        return action.toActionEffect()
    }

    private companion object {
        /** How many events the month placeholder lists before its overflow takes the rest. */
        const val VISIBLE_MONTH_EVENTS = 3

        /** Material's minimum touch target. */
        val MINIMUM_TOUCH_TARGET = 48.dp

        val EVENT_IDS = listOf("s1", "s2", "s3", "s4")
        val EVENT_TITLES = listOf("สงกรานต์", "ทริปเชียงใหม่", "ประชุมนอกสถานที่", "Offsite")

        /** A month with one more event than it lists, and a tab onto itself. */
        val LOADED =
            CalendarUiState(
                content =
                    calendarScreenResponse(
                        body =
                            monthBody(
                                events =
                                    EVENT_IDS.zip(EVENT_TITLES) { id, title ->
                                        allDayEvent(id, MonthAllDayBarPresentation(title = title))
                                    }
                            ),
                        destinations =
                            listOf(
                                NavDestination(
                                    id = CALENDAR_SCREEN_ID,
                                    label = "ปฏิทิน",
                                    action = NavigateAction(CALENDAR_SCREEN_ID),
                                )
                            ),
                    ),
                isLoading = false,
                failure = null,
            )
    }
}
