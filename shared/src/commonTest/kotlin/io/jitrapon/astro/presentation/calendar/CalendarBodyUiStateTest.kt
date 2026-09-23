package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.AgendaBody
import io.jitrapon.astro.data.calendar.AgendaDay
import io.jitrapon.astro.data.calendar.CalendarAgendaViewModel
import io.jitrapon.astro.data.calendar.CalendarComponentIds
import io.jitrapon.astro.data.calendar.CalendarMonthViewModel
import io.jitrapon.astro.data.calendar.CalendarScreen
import io.jitrapon.astro.data.calendar.EventCardPresentation
import io.jitrapon.astro.data.calendar.MonthAllDayBarPresentation
import io.jitrapon.astro.data.calendar.MonthBody
import io.jitrapon.astro.data.calendar.MonthTimedMarkerPresentation
import io.jitrapon.astro.data.calendar.PresentationLine
import io.jitrapon.astro.data.calendar.PresentedCalendarEvent
import io.jitrapon.astro.data.calendar.decodeMonthScreenFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins how each calendar body a screen can deliver becomes the state its renderer reads, starting
 * from the vendored contract fixture so every case is shaped the way the server actually sends it.
 */
class CalendarBodyUiStateTest {

    @Test
    fun theFixtureMonthBodyKeepsItsHeadingAnchorWeekStartAndEventOrder() {
        val screen = fixtureScreen()
        val body = screen.monthBody()

        val state = body.toMonthBodyUiState(screen.resolvedPreferences)

        assertEquals(CalendarComponentIds.MONTH_BODY, state.componentId)
        assertEquals(body.props.headerLabel, state.headerLabel)
        assertEquals(body.props.monthAnchor, state.monthAnchor)
        assertEquals(screen.resolvedPreferences.weekStart, state.weekStart)
        assertEquals(body.props.events.map { it.props.id }, state.events.map { it.eventId })
        assertEquals(
            body.props.events.map { it.presentation.componentId },
            state.events.map { it.componentId },
        )
    }

    @Test
    fun aMonthAllDayBarTakesItsTitleChipStyleAndCalendarColour() {
        val screen = fixtureScreen()
        val body = screen.monthBody()
        val event = body.props.events.first { it.presentation is MonthAllDayBarPresentation }
        val presentation = event.presentation as MonthAllDayBarPresentation

        val chip = event.toEventChipUiState(body.props.calendars, screen.resolvedPreferences)

        assertEquals(presentation.title, chip.title)
        assertEquals(screen.resolvedPreferences.chipStyle, chip.chipStyle)
        assertEquals(
            body.props.calendars.getValue(event.props.calendarId).color,
            chip.calendarColor,
        )
        assertEquals(presentation.accessibilityLabel, chip.accessibilityLabel)
    }

    @Test
    fun aMonthTimedMarkerTakesItsCombinedLineAsTitleAndNoChipStyle() {
        val screen = fixtureScreen()
        val body = screen.monthBody()
        val event = body.props.events.first { it.presentation is MonthTimedMarkerPresentation }
        val presentation = event.presentation as MonthTimedMarkerPresentation

        val chip = event.toEventChipUiState(body.props.calendars, screen.resolvedPreferences)

        assertEquals(CalendarComponentIds.MONTH_TIMED_MARKER, chip.componentId)
        assertEquals(presentation.line.text, chip.title)
        assertEquals(presentation.line.iconToken, chip.leadingIconToken)
        assertTrue(chip.subtitleLines.isEmpty())
        assertNull(chip.chipStyle)
    }

    @Test
    fun anEventWithMoreSubtitleLinesThanTheCapKeepsOnlyTheFirstOnes() {
        val screen = fixtureScreen()
        val cap = screen.resolvedPreferences.chipDensity.maxSubtitleLines
        val lines = List(cap + 2) { PresentationLine("line $it") }
        val body = screen.monthBody().withFirstAllDayBarSubtitleLines(lines)

        val state = body.toMonthBodyUiState(screen.resolvedPreferences)

        val chip = state.events.first { it.componentId == CalendarComponentIds.MONTH_ALL_DAY_BAR }
        assertEquals(lines.take(cap), chip.subtitleLines)
    }

    @Test
    fun anAgendaBodyKeepsItsDaysInOrderWithTheirCardsAndNoChipStyle() {
        val screen = fixtureScreen()
        val agenda = screen.monthBody().props.asAgenda()

        val state = AgendaBody(agenda).toAgendaBodyUiState(screen.resolvedPreferences)

        assertEquals(CalendarComponentIds.AGENDA_BODY, state.componentId)
        assertEquals(agenda.days.map { it.date }, state.days.map { it.date })
        assertEquals(agenda.days.map { it.headerLabel }, state.days.map { it.headerLabel })
        val chips = state.days.flatMap { it.events }
        assertEquals(
            agenda.days.flatMap { day -> day.events.map { it.props.id } },
            chips.map { it.eventId },
        )
        assertTrue(chips.all { it.componentId == CalendarComponentIds.EVENT_CARD })
        assertTrue(chips.all { it.chipStyle == null })
        assertEquals(
            agenda.days.flatMap { day ->
                day.events.map { agenda.calendars.getValue(it.props.calendarId).color }
            },
            chips.map { it.calendarColor },
        )
    }
}

private fun fixtureScreen(): CalendarScreen = decodeMonthScreenFixture().screen

private fun CalendarScreen.monthBody(): MonthBody = body as MonthBody

/** This body with its first all-day bar carrying [lines] as subtitles. */
private fun MonthBody.withFirstAllDayBarSubtitleLines(lines: List<PresentationLine>): MonthBody {
    val index = props.events.indexOfFirst { it.presentation is MonthAllDayBarPresentation }
    val event = props.events[index]
    val bar = (event.presentation as MonthAllDayBarPresentation).copy(subtitleLines = lines)
    val events = props.events.toMutableList().apply { set(index, event.copy(presentation = bar)) }
    return copy(props = props.copy(events = events))
}

/**
 * The fixture's month events regrouped as a two-day agenda — the fixture carries no agenda body, so
 * this builds one from the same calendars and events the server delivered.
 */
private fun CalendarMonthViewModel.asAgenda(): CalendarAgendaViewModel {
    val cards = events.map { it.asCard() }
    val (first, second) = cards.chunked((cards.size + 1) / 2)
    return CalendarAgendaViewModel(
        range = range,
        calendars = calendars,
        days =
            listOf(
                AgendaDay(date = range.start, headerLabel = "first day", events = first),
                AgendaDay(date = range.end, headerLabel = "second day", events = second),
            ),
    )
}

private fun PresentedCalendarEvent.asCard(): PresentedCalendarEvent =
    copy(
        presentation =
            EventCardPresentation(
                title = "card ${props.id}",
                subtitleLines = listOf(PresentationLine("subtitle ${props.id}")),
                accessibilityLabel = presentation.accessibilityLabel,
            )
    )
