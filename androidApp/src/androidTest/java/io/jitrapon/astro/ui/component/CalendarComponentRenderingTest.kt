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
import io.jitrapon.astro.data.calendar.AgendaBody
import io.jitrapon.astro.data.calendar.AgendaDay
import io.jitrapon.astro.data.calendar.AllDayEvent
import io.jitrapon.astro.data.calendar.Calendar
import io.jitrapon.astro.data.calendar.CalendarAgendaViewModel
import io.jitrapon.astro.data.calendar.CalendarBody
import io.jitrapon.astro.data.calendar.CalendarColor
import io.jitrapon.astro.data.calendar.CalendarItemType
import io.jitrapon.astro.data.calendar.CalendarMonthViewModel
import io.jitrapon.astro.data.calendar.CalendarRange
import io.jitrapon.astro.data.calendar.CalendarScreen
import io.jitrapon.astro.data.calendar.CalendarScreenResponse
import io.jitrapon.astro.data.calendar.ChipDensity
import io.jitrapon.astro.data.calendar.ChipDensityLevel
import io.jitrapon.astro.data.calendar.ChipStyle
import io.jitrapon.astro.data.calendar.EventCardPresentation
import io.jitrapon.astro.data.calendar.EventPermissions
import io.jitrapon.astro.data.calendar.EventPresentation
import io.jitrapon.astro.data.calendar.MonthAllDayBarPresentation
import io.jitrapon.astro.data.calendar.MonthBody
import io.jitrapon.astro.data.calendar.MonthViewSelection
import io.jitrapon.astro.data.calendar.Navigation
import io.jitrapon.astro.data.calendar.PresentationLine
import io.jitrapon.astro.data.calendar.PresentedCalendarEvent
import io.jitrapon.astro.data.calendar.ResolvedPreferences
import io.jitrapon.astro.data.calendar.ThemeRef
import io.jitrapon.astro.data.calendar.ViewSwitcher
import io.jitrapon.astro.data.calendar.WeekStart
import io.jitrapon.astro.presentation.calendar.CalendarUiState
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
        const val CALENDAR_ID = "cal-work"
        val SUBTITLES = listOf("Office", "Floor 3", "Bring laptop")
        val AGENDA_DAY_HEADERS = listOf("พฤ. 16 เม.ย.", "ศ. 17 เม.ย.")

        fun monthScreen(weekStart: WeekStart, maxSubtitleLines: Int): CalendarScreenResponse =
            screenWith(
                weekStart = weekStart,
                maxSubtitleLines = maxSubtitleLines,
                body =
                    MonthBody(
                        CalendarMonthViewModel(
                            range = CalendarRange(start = "2026-03-30", end = "2026-05-10"),
                            monthAnchor = "2026-04-01",
                            headerLabel = MONTH_HEADER,
                            calendars = CALENDARS,
                            events =
                                listOf(
                                    event(
                                        id = ALL_DAY_EVENT_ID,
                                        presentation =
                                            MonthAllDayBarPresentation(
                                                title = "สงกรานต์",
                                                subtitleLines = SUBTITLES.map(::PresentationLine),
                                            ),
                                    )
                                ),
                        )
                    ),
            )

        fun agendaScreen(): CalendarScreenResponse =
            screenWith(
                weekStart = WeekStart.MONDAY,
                maxSubtitleLines = 1,
                body =
                    AgendaBody(
                        CalendarAgendaViewModel(
                            range = CalendarRange(start = "2026-04-16", end = "2026-04-17"),
                            calendars = CALENDARS,
                            days =
                                listOf(
                                    AgendaDay(
                                        date = "2026-04-16",
                                        headerLabel = AGENDA_DAY_HEADERS[0],
                                        events =
                                            listOf(
                                                event(
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
                    ),
            )

        fun screenWith(
            weekStart: WeekStart,
            maxSubtitleLines: Int,
            body: CalendarBody,
        ): CalendarScreenResponse =
            CalendarScreenResponse(
                schemaVersion = "0.2.0",
                serverTime = "2026-04-16T03:30:00Z",
                locale = "th-TH",
                timeZone = "Asia/Bangkok",
                theme = ThemeRef(id = "light", version = "1"),
                screen =
                    CalendarScreen(
                        id = "calendar",
                        title = MONTH_HEADER,
                        navigation = Navigation(emptyList()),
                        viewSwitcher =
                            ViewSwitcher(
                                activeSelection = MonthViewSelection,
                                options = emptyList(),
                            ),
                        resolvedPreferences =
                            ResolvedPreferences(
                                weekStart = weekStart,
                                chipStyle = ChipStyle.PASTEL,
                                chipDensity =
                                    ChipDensity(
                                        level = ChipDensityLevel.COMFORTABLE,
                                        maxSubtitleLines = maxSubtitleLines,
                                    ),
                            ),
                        body = body,
                    ),
            )

        fun event(id: String, presentation: EventPresentation): PresentedCalendarEvent =
            PresentedCalendarEvent(
                itemType = CalendarItemType.CALENDAR_EVENT_V1,
                presentation = presentation,
                props =
                    AllDayEvent(
                        id = id,
                        calendarId = CALENDAR_ID,
                        permissions =
                            EventPermissions(canEdit = true, canDelete = true, canMove = true),
                        version = "$id:v1",
                        startDate = "2026-04-16",
                        endDate = "2026-04-16",
                    ),
            )

        val CALENDARS =
            mapOf(
                CALENDAR_ID to
                    Calendar(
                        id = CALENDAR_ID,
                        displayName = "งาน",
                        color =
                            CalendarColor(
                                accentToken = "calendar.work.accent",
                                accentColor = "#b81311",
                                backgroundToken = "calendar.work.background",
                                backgroundColor = "#f9dcda",
                                foregroundToken = "calendar.work.foreground",
                                foregroundColor = "#5c0a09",
                            ),
                    )
            )
    }
}
