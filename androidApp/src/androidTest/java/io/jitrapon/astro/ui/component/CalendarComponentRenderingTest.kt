package io.jitrapon.astro.ui.component

import android.view.ViewGroup
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.jitrapon.astro.WORK_CALENDARS
import io.jitrapon.astro.allDayEvent
import io.jitrapon.astro.calendarScreenResponse
import io.jitrapon.astro.data.calendar.AgendaBody
import io.jitrapon.astro.data.calendar.AgendaDay
import io.jitrapon.astro.data.calendar.CalendarAgendaViewModel
import io.jitrapon.astro.data.calendar.CalendarRange
import io.jitrapon.astro.data.calendar.CalendarScreenResponse
import io.jitrapon.astro.data.calendar.EventCardPresentation
import io.jitrapon.astro.data.calendar.MonthAllDayBarPresentation
import io.jitrapon.astro.data.calendar.PresentationLine
import io.jitrapon.astro.data.calendar.WeekStart
import io.jitrapon.astro.monthBody
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.resolvedPreferences
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import java.text.DateFormatSymbols
import java.util.Calendar.MONDAY
import java.util.Calendar.SATURDAY
import java.util.Calendar.SUNDAY
import org.junit.Rule
import org.junit.Test

/**
 * Pins that each placeholder renderer draws its own component's props, reached the way the app
 * reaches them: a delivered screen projected through `CalendarUiState.body`, so the resolved
 * preferences are applied by the shared projection rather than hand-applied in the test.
 */
class CalendarComponentRenderingTest {

    // Hosted in the app's own activity for the same reason as the shell tests: instrumented tests
    // run against the minified release build, which has no debug-only test manifest.
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theMonthPlaceholderShowsItsHeading() {
        show(monthScreen(weekStart = WeekStart.MONDAY, maxSubtitleLines = 1))

        composeRule.onNodeWithText(MONTH_HEADER).assertIsDisplayed()
    }

    @Test
    fun aChipWithMoreSubtitleLinesThanTheCapDrawsExactlyTheCap() {
        show(monthScreen(weekStart = WeekStart.MONDAY, maxSubtitleLines = 1))

        composeRule
            .onAllNodesWithTag(CalendarComponentTestTags.subtitleLine(ALL_DAY_EVENT_ID))
            .assertCountEquals(1)
        composeRule.onNodeWithText(SUBTITLES.first()).assertIsDisplayed()
    }

    @Test
    fun aCapOfZeroDrawsNoSubtitleLines() {
        show(monthScreen(weekStart = WeekStart.MONDAY, maxSubtitleLines = 0))

        composeRule
            .onAllNodesWithTag(CalendarComponentTestTags.subtitleLine(ALL_DAY_EVENT_ID))
            .assertCountEquals(0)
    }

    @Test
    fun aMondayWeekStartOrdersTheWeekdayRowFromMonday() {
        show(monthScreen(weekStart = WeekStart.MONDAY, maxSubtitleLines = 1))

        composeRule.assertWeekdayRowRuns(from = MONDAY, to = SUNDAY)
    }

    @Test
    fun aSundayWeekStartOrdersTheWeekdayRowFromSunday() {
        show(monthScreen(weekStart = WeekStart.SUNDAY, maxSubtitleLines = 1))

        composeRule.assertWeekdayRowRuns(from = SUNDAY, to = SATURDAY)
    }

    @Test
    fun theAgendaPlaceholderShowsEachDaysHeadingAndItsCards() {
        show(agendaScreen())

        AGENDA_DAY_HEADERS.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText(CARD_TITLE).assertIsDisplayed()
    }

    /**
     * Asserts the month's weekday row begins on [from] and ends on [to], in this locale's names.
     */
    private fun ComposeContentTestRule.assertWeekdayRowRuns(
        from: Int,
        to: Int,
    ) {
        val names = DateFormatSymbols.getInstance().shortWeekdays
        onNodeWithTag(CalendarComponentTestTags.weekday(0)).assertTextEquals(names[from])
        onNodeWithTag(CalendarComponentTestTags.weekday(LAST_WEEKDAY_POSITION))
            .assertTextEquals(names[to])
    }

    // Must be called once per test: the rule refuses a second `setContent`.
    private fun show(screen: CalendarScreenResponse) {
        val body =
            checkNotNull(CalendarUiState(content = screen, isLoading = false, failure = null).body)
        // MainActivity composes the live shell in `onCreate`, and the rule refuses to set content
        // over an activity that already has some. Detach it first so only this body renders.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        }
        composeRule.setContent { AstroTheme { CalendarBodyComponent(body) } }
        composeRule.waitForIdle()
    }

    private companion object {
        /** The weekday row's last position: a week has seven days, counted from 0. */
        const val LAST_WEEKDAY_POSITION = 6
        const val MONTH_HEADER = "เมษายน 2569"
        const val ALL_DAY_EVENT_ID = "s1"
        const val CARD_TITLE = "Standup"
        val SUBTITLES = listOf("Office", "Floor 3", "Bring laptop")
        val AGENDA_DAY_HEADERS = listOf("พฤ. 16 เม.ย.", "ศ. 17 เม.ย.")

        fun monthScreen(weekStart: WeekStart, maxSubtitleLines: Int): CalendarScreenResponse =
            calendarScreenResponse(
                preferences = resolvedPreferences(weekStart, maxSubtitleLines),
                body =
                    monthBody(
                        headerLabel = MONTH_HEADER,
                        events =
                            listOf(
                                allDayEvent(
                                    id = ALL_DAY_EVENT_ID,
                                    presentation =
                                        MonthAllDayBarPresentation(
                                            title = "สงกรานต์",
                                            subtitleLines = SUBTITLES.map(::PresentationLine),
                                        ),
                                )
                            ),
                    ),
            )

        fun agendaScreen(): CalendarScreenResponse =
            calendarScreenResponse(
                body =
                    AgendaBody(
                        CalendarAgendaViewModel(
                            range = CalendarRange(start = "2026-04-16", end = "2026-04-17"),
                            calendars = WORK_CALENDARS,
                            days =
                                listOf(
                                    AgendaDay(
                                        date = "2026-04-16",
                                        headerLabel = AGENDA_DAY_HEADERS[0],
                                        events =
                                            listOf(
                                                allDayEvent(
                                                    id = "c1",
                                                    presentation =
                                                        EventCardPresentation(title = CARD_TITLE),
                                                )
                                            ),
                                    ),
                                    AgendaDay(
                                        date = "2026-04-17",
                                        headerLabel = AGENDA_DAY_HEADERS[1],
                                        events = emptyList(),
                                    ),
                                ),
                        )
                    )
            )
    }
}
