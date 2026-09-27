package io.jitrapon.astro

import io.jitrapon.astro.data.calendar.AllDayEvent
import io.jitrapon.astro.data.calendar.Calendar
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
import io.jitrapon.astro.data.calendar.EventPermissions
import io.jitrapon.astro.data.calendar.EventPresentation
import io.jitrapon.astro.data.calendar.MonthBody
import io.jitrapon.astro.data.calendar.MonthViewSelection
import io.jitrapon.astro.data.calendar.NavDestination
import io.jitrapon.astro.data.calendar.Navigation
import io.jitrapon.astro.data.calendar.PresentedCalendarEvent
import io.jitrapon.astro.data.calendar.ResolvedPreferences
import io.jitrapon.astro.data.calendar.ThemeRef
import io.jitrapon.astro.data.calendar.ViewSwitcher
import io.jitrapon.astro.data.calendar.WeekStart

// Delivered calendar screens built in code for instrumented tests. They are built rather than
// decoded because a test that decodes would also be testing the shrunk app's serializers — which
// the minified smoke test covers on its own.

/** The id the screens below are delivered under, and the target a navigating tab names for it. */
const val CALENDAR_SCREEN_ID = "calendar"

/** The one calendar the screens' events belong to. */
const val WORK_CALENDAR_ID = "cal-work"

/** A calendar screen response carrying [body], with every envelope field filled plausibly. */
fun calendarScreenResponse(
    body: CalendarBody,
    title: String = "เมษายน 2569",
    destinations: List<NavDestination> = emptyList(),
    viewSwitcher: ViewSwitcher = ViewSwitcher(MonthViewSelection, options = emptyList()),
    preferences: ResolvedPreferences = resolvedPreferences(),
): CalendarScreenResponse =
    CalendarScreenResponse(
        schemaVersion = "0.2.0",
        serverTime = "2026-04-16T03:30:00Z",
        locale = "th-TH",
        timeZone = "Asia/Bangkok",
        theme = ThemeRef(id = "light", version = "1"),
        screen =
            CalendarScreen(
                id = CALENDAR_SCREEN_ID,
                title = title,
                navigation = Navigation(destinations),
                viewSwitcher = viewSwitcher,
                resolvedPreferences = preferences,
                body = body,
            ),
    )

/** Resolved preferences starting weeks on [weekStart] and capping chips at [maxSubtitleLines]. */
fun resolvedPreferences(
    weekStart: WeekStart = WeekStart.MONDAY,
    maxSubtitleLines: Int = 1,
): ResolvedPreferences =
    ResolvedPreferences(
        weekStart = weekStart,
        chipStyle = ChipStyle.PASTEL,
        chipDensity =
            ChipDensity(level = ChipDensityLevel.COMFORTABLE, maxSubtitleLines = maxSubtitleLines),
    )

/** An April 2026 month body headed [headerLabel] holding [events]. */
fun monthBody(
    headerLabel: String = "เมษายน 2569",
    events: List<PresentedCalendarEvent> = emptyList(),
): MonthBody =
    MonthBody(
        CalendarMonthViewModel(
            range = CalendarRange(start = "2026-03-30", end = "2026-05-10"),
            monthAnchor = "2026-04-01",
            headerLabel = headerLabel,
            calendars = WORK_CALENDARS,
            events = events,
        )
    )

/** A one-day all-day event on the work calendar, presented as [presentation]. */
fun allDayEvent(id: String, presentation: EventPresentation): PresentedCalendarEvent =
    PresentedCalendarEvent(
        itemType = CalendarItemType.CALENDAR_EVENT_V1,
        presentation = presentation,
        props =
            AllDayEvent(
                id = id,
                calendarId = WORK_CALENDAR_ID,
                permissions = EventPermissions(canEdit = true, canDelete = true, canMove = true),
                version = "$id:v1",
                startDate = "2026-04-16",
                endDate = "2026-04-16",
            ),
    )

/** The calendars map every screen above carries: just the work calendar, with its colours. */
val WORK_CALENDARS: Map<String, Calendar> =
    mapOf(
        WORK_CALENDAR_ID to
            Calendar(
                id = WORK_CALENDAR_ID,
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
