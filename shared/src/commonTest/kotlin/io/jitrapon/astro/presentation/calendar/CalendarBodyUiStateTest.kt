package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.AgendaBody
import io.jitrapon.astro.data.calendar.AgendaDay
import io.jitrapon.astro.data.calendar.AllDayEvent
import io.jitrapon.astro.data.calendar.CalendarAgendaViewModel
import io.jitrapon.astro.data.calendar.CalendarComponentIds
import io.jitrapon.astro.data.calendar.CalendarEvent
import io.jitrapon.astro.data.calendar.CalendarMonthViewModel
import io.jitrapon.astro.data.calendar.CalendarScreen
import io.jitrapon.astro.data.calendar.ChipDensity
import io.jitrapon.astro.data.calendar.ChipDensityLevel
import io.jitrapon.astro.data.calendar.ChipStyle
import io.jitrapon.astro.data.calendar.EventBlockPresentation
import io.jitrapon.astro.data.calendar.EventCardPresentation
import io.jitrapon.astro.data.calendar.MonthAllDayBarPresentation
import io.jitrapon.astro.data.calendar.MonthBody
import io.jitrapon.astro.data.calendar.MonthTimedMarkerPresentation
import io.jitrapon.astro.data.calendar.PresentationLine
import io.jitrapon.astro.data.calendar.PresentedCalendarEvent
import io.jitrapon.astro.data.calendar.TimeGridAllDayBarPresentation
import io.jitrapon.astro.data.calendar.TimedEvent
import io.jitrapon.astro.data.calendar.WeekStart
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

        val state =
            body.toMonthBodyUiState(
                screen.resolvedPreferences,
                screenTitle = HEADING_THE_TITLE_DOES_NOT_REPEAT,
            )

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
    fun aMonthHeadingThatRepeatsTheScreenTitleIsDropped() {
        val screen = fixtureScreen()

        val state = screen.toCalendarBodyUiState() as MonthBodyUiState

        assertEquals(screen.title, screen.monthBody().props.headerLabel)
        assertNull(state.headerLabel, "The month was named by both the title and the heading.")
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

        val state =
            body.toMonthBodyUiState(
                screen.resolvedPreferences,
                screenTitle = HEADING_THE_TITLE_DOES_NOT_REPEAT,
            )

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

    @Test
    fun aScreenProjectsEachBodyItCanDeliverOntoTheMatchingState() {
        val screen = fixtureScreen()
        val agendaScreen = screen.copy(body = AgendaBody(screen.monthBody().props.asAgenda()))

        assertEquals(
            screen
                .monthBody()
                .toMonthBodyUiState(screen.resolvedPreferences, screenTitle = screen.title),
            screen.toCalendarBodyUiState(),
        )
        assertEquals(
            (agendaScreen.body as AgendaBody).toAgendaBodyUiState(screen.resolvedPreferences),
            agendaScreen.toCalendarBodyUiState(),
        )
    }

    @Test
    fun subtitleLinesAreCappedAtTheResolvedMaxSubtitleLinesIncludingZero() {
        val lines = List(OVER_CAP_LINE_COUNT) { PresentationLine("line $it") }
        val base = fixtureScreen().withMonthEvents { it.withFirstAllDayBarSubtitleLines(lines) }

        for (cap in 0..lines.size + 1) {
            val chip = base.withChipDensity(maxSubtitleLines = cap).firstAllDayBarChip()

            assertEquals(lines.take(cap), chip.subtitleLines, "maxSubtitleLines = $cap")
        }
    }

    @Test
    fun theSubtitleCapIsReadFromMaxSubtitleLinesAloneNeverFromTheDensityLevel() {
        val lines = List(OVER_CAP_LINE_COUNT) { PresentationLine("line $it") }
        val base = fixtureScreen().withMonthEvents { it.withFirstAllDayBarSubtitleLines(lines) }

        for (level in ChipDensityLevel.entries) {
            val none = base.withChipDensity(level, maxSubtitleLines = 0).firstAllDayBarChip()
            val all = base.withChipDensity(level, lines.size).firstAllDayBarChip()

            assertTrue(none.subtitleLines.isEmpty(), "level = $level with a zero cap")
            assertEquals(lines, all.subtitleLines, "level = $level with a cap covering every line")
        }
    }

    @Test
    fun aNegativeCapDrawsNoSubtitleLinesRatherThanFailingTheBody() {
        val lines = List(2) { PresentationLine("line $it") }
        val screen =
            fixtureScreen()
                .withMonthEvents { it.withFirstAllDayBarSubtitleLines(lines) }
                .withChipDensity(maxSubtitleLines = -1)

        assertTrue(screen.firstAllDayBarChip().subtitleLines.isEmpty())
    }

    @Test
    fun chipStyleReachesOnlyTheFilledBarComponents() {
        val filledBars =
            setOf(
                CalendarComponentIds.MONTH_ALL_DAY_BAR,
                CalendarComponentIds.TIME_GRID_ALL_DAY_BAR,
            )
        val base = fixtureScreen().withMonthEvents { it.withOneEventPerPresentation() }

        for (style in ChipStyle.entries) {
            val screen =
                base.copy(resolvedPreferences = base.resolvedPreferences.copy(chipStyle = style))
            val chips = (screen.toCalendarBodyUiState() as MonthBodyUiState).events

            assertEquals(ALL_EVENT_PRESENTATION_IDS, chips.map { it.componentId }.toSet())
            for (chip in chips) {
                val expected = style.takeIf { chip.componentId in filledBars }
                assertEquals(expected, chip.chipStyle, "${chip.componentId} under $style")
            }
        }
    }

    @Test
    fun weekStartIsCarriedOntoTheMonthBodyUnmodified() {
        val base = fixtureScreen()

        for (weekStart in WeekStart.entries) {
            val screen =
                base.copy(
                    resolvedPreferences = base.resolvedPreferences.copy(weekStart = weekStart)
                )

            assertEquals(weekStart, (screen.toCalendarBodyUiState() as MonthBodyUiState).weekStart)
        }
    }

    @Test
    fun anEventWhoseCalendarIsMissingResolvesToNoColourWhileTheRestKeepTheirs() {
        val screen =
            fixtureScreen().withMonthEvents { events ->
                events.mapIndexed { index, event ->
                    if (index == 0)
                        event.copy(props = event.props.withIdentity(calendarId = "absent"))
                    else event
                }
            }
        val body = screen.monthBody()

        val chips = (screen.toCalendarBodyUiState() as MonthBodyUiState).events

        assertNull(chips.first().calendarColor)
        assertEquals(
            body.props.events.drop(1).map {
                body.props.calendars.getValue(it.props.calendarId).color
            },
            chips.drop(1).map { it.calendarColor },
        )
    }
}

/**
 * More subtitle lines than any cap the fixture resolves, so a test can cap below, at, and above it.
 */
private const val OVER_CAP_LINE_COUNT = 3

private val ALL_EVENT_PRESENTATION_IDS =
    setOf(
        CalendarComponentIds.MONTH_ALL_DAY_BAR,
        CalendarComponentIds.MONTH_TIMED_MARKER,
        CalendarComponentIds.TIME_GRID_ALL_DAY_BAR,
        CalendarComponentIds.EVENT_BLOCK,
        CalendarComponentIds.EVENT_CARD,
    )

/** This screen with its month body's events replaced by [transform]'s result. */
private fun CalendarScreen.withMonthEvents(
    transform: (List<PresentedCalendarEvent>) -> List<PresentedCalendarEvent>
): CalendarScreen {
    val body = monthBody()
    return copy(body = body.copy(props = body.props.copy(events = transform(body.props.events))))
}

private fun CalendarScreen.withChipDensity(
    level: ChipDensityLevel = resolvedPreferences.chipDensity.level,
    maxSubtitleLines: Int,
): CalendarScreen =
    copy(
        resolvedPreferences =
            resolvedPreferences.copy(chipDensity = ChipDensity(level, maxSubtitleLines))
    )

private fun CalendarScreen.firstAllDayBarChip(): EventChipUiState =
    (toCalendarBodyUiState() as MonthBodyUiState).events.first {
        it.componentId == CalendarComponentIds.MONTH_ALL_DAY_BAR
    }

/**
 * The first event re-presented once as each of the five event-presentation components, each copy
 * carrying one subtitle line — the contract lets any of them arrive, so the projection must decide
 * chip style for every one.
 */
private fun List<PresentedCalendarEvent>.withOneEventPerPresentation():
    List<PresentedCalendarEvent> {
    val event = first()
    val lines = listOf(PresentationLine("subtitle"))
    return listOf(
            MonthAllDayBarPresentation(title = "bar", subtitleLines = lines),
            MonthTimedMarkerPresentation(line = PresentationLine("09:00 marker")),
            TimeGridAllDayBarPresentation(title = "grid bar", subtitleLines = lines),
            EventBlockPresentation(title = "block", subtitleLines = lines),
            EventCardPresentation(title = "card", subtitleLines = lines),
        )
        .mapIndexed { index, presentation ->
            event.copy(
                presentation = presentation,
                props = event.props.withIdentity(id = "${event.props.id}-$index"),
            )
        }
}

private fun fixtureScreen(): CalendarScreen = decodeMonthScreenFixture().screen

private fun CalendarScreen.monthBody(): MonthBody = body as MonthBody

/** This body with its first all-day bar carrying [lines] as subtitles. */
private fun MonthBody.withFirstAllDayBarSubtitleLines(lines: List<PresentationLine>): MonthBody =
    copy(props = props.copy(events = props.events.withFirstAllDayBarSubtitleLines(lines)))

/** These events with the first all-day bar carrying [lines] as subtitles. */
private fun List<PresentedCalendarEvent>.withFirstAllDayBarSubtitleLines(
    lines: List<PresentationLine>
): List<PresentedCalendarEvent> {
    val index = indexOfFirst { it.presentation is MonthAllDayBarPresentation }
    val event = this[index]
    val bar = (event.presentation as MonthAllDayBarPresentation).copy(subtitleLines = lines)
    return toMutableList().apply { set(index, event.copy(presentation = bar)) }
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

/** This event with [id] and [calendarId] replaced, whichever temporal branch it is. */
private fun CalendarEvent.withIdentity(
    id: String = this.id,
    calendarId: String = this.calendarId,
): CalendarEvent =
    when (this) {
        is TimedEvent -> copy(id = id, calendarId = calendarId)
        is AllDayEvent -> copy(id = id, calendarId = calendarId)
    }

/** A screen title no fixture heading equals, so the heading under test is kept. */
private const val HEADING_THE_TITLE_DOES_NOT_REPEAT = "Calendar"
