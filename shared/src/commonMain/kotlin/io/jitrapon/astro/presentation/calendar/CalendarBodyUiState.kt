package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.AgendaBody
import io.jitrapon.astro.data.calendar.AgendaDay
import io.jitrapon.astro.data.calendar.Calendar
import io.jitrapon.astro.data.calendar.CalendarColor
import io.jitrapon.astro.data.calendar.CalendarScreen
import io.jitrapon.astro.data.calendar.ChipStyle
import io.jitrapon.astro.data.calendar.EventBlockPresentation
import io.jitrapon.astro.data.calendar.EventCardPresentation
import io.jitrapon.astro.data.calendar.EventPresentation
import io.jitrapon.astro.data.calendar.MonthAllDayBarPresentation
import io.jitrapon.astro.data.calendar.MonthBody
import io.jitrapon.astro.data.calendar.MonthTimedMarkerPresentation
import io.jitrapon.astro.data.calendar.PresentationLine
import io.jitrapon.astro.data.calendar.PresentedCalendarEvent
import io.jitrapon.astro.data.calendar.ResolvedPreferences
import io.jitrapon.astro.data.calendar.TimeGridAllDayBarPresentation
import io.jitrapon.astro.data.calendar.WeekStart

/**
 * The delivered calendar body, reduced to what a platform renderer draws: the component to resolve
 * a renderer by, and props with the screen's resolved preferences already applied.
 *
 * A renderer reads only this, never the decoded body beside it, so no platform re-decides how many
 * subtitle lines to show, which components a chip style reaches, or which colour an event takes
 * from its calendar.
 */
sealed interface CalendarBodyUiState {
    /**
     * The versioned component name the body was delivered as — the key each platform's component
     * registry resolves its renderer by.
     */
    val componentId: String
}

/** A month grid body. */
data class MonthBodyUiState(
    override val componentId: String,
    /** Server-formatted month and year heading. */
    val headerLabel: String,
    /** First day of the month the view is centred on. */
    val monthAnchor: String,
    /** The weekday the grid's rows start on, as the user's preferences resolved it. */
    val weekStart: WeekStart,
    /**
     * Every event placed in the month, in the server's display order — which a renderer keeps,
     * since re-sorting loses the tiebreak that keeps optimistic updates stable.
     */
    val events: List<EventChipUiState>,
) : CalendarBodyUiState

/** An agenda list body. */
data class AgendaBodyUiState(
    override val componentId: String,
    /** One section per day, in delivered order. */
    val days: List<AgendaDayUiState>,
) : CalendarBodyUiState

/** One day's section of an agenda list. */
data class AgendaDayUiState(
    val date: String,
    /** Server-formatted day heading. */
    val headerLabel: String,
    /** The day's events, in the server's display order. */
    val events: List<EventChipUiState>,
)

/** One event as a renderer draws it, whichever event-presentation component it arrived as. */
data class EventChipUiState(
    /**
     * The canonical event's id — what a tap on the chip names, since the contract attaches no
     * action to an event and the client builds the one a tap dispatches.
     */
    val eventId: String,
    /** The versioned event-presentation component — the key a registry resolves a renderer by. */
    val componentId: String,
    /**
     * The chip's primary text. A month timed marker carries no title of its own, so for it this is
     * the single server-composed line (time and title together).
     */
    val title: String,
    /** The semantic icon leading the chip, or `null` when the presentation names none. */
    val leadingIconToken: String?,
    /**
     * The subtitle lines to render, already capped at the resolved chip density — a renderer draws
     * every line here and derives no cap of its own.
     */
    val subtitleLines: List<PresentationLine>,
    /**
     * The fill treatment to draw, or `null` for a component chip style does not reach. The contract
     * scopes it to the filled-bar components only; a timed marker, a time-grid block and an agenda
     * card have no fill for it to apply to.
     */
    val chipStyle: ChipStyle?,
    /**
     * The colour triple of the calendar the event belongs to, or `null` when the body's calendars
     * carry no entry for it — a renderer then falls back to its own neutral colours rather than the
     * projection failing the whole body over one unresolvable reference.
     */
    val calendarColor: CalendarColor?,
    val accessibilityLabel: String?,
)

/**
 * This screen's body as the state its renderer reads, with the screen's resolved preferences
 * applied — the one place they reach a body:
 * - `chipDensity.maxSubtitleLines` caps every event's subtitle lines;
 * - `chipStyle` reaches only the filled-bar components (the month and time-grid all-day bars);
 * - `weekStart` is carried onto a month body unmodified, for its grid to order rows from.
 *
 * `chipDensity`'s two fields are each authoritative for one thing — `level` for which preset is
 * active, `maxSubtitleLines` for how many lines to draw — and the server may remap one without the
 * other changing, so neither this projection nor any client re-derives either from the other.
 */
internal fun CalendarScreen.toCalendarBodyUiState(): CalendarBodyUiState =
    when (val body = body) {
        is MonthBody -> body.toMonthBodyUiState(resolvedPreferences)
        is AgendaBody -> body.toAgendaBodyUiState(resolvedPreferences)
    }

/** This month body with [preferences] applied to its week start and every event it places. */
internal fun MonthBody.toMonthBodyUiState(preferences: ResolvedPreferences): MonthBodyUiState =
    MonthBodyUiState(
        componentId = componentId,
        headerLabel = props.headerLabel,
        monthAnchor = props.monthAnchor,
        weekStart = preferences.weekStart,
        events = props.events.map { it.toEventChipUiState(props.calendars, preferences) },
    )

/** This agenda body with [preferences] applied to every event in every day. */
internal fun AgendaBody.toAgendaBodyUiState(preferences: ResolvedPreferences): AgendaBodyUiState =
    AgendaBodyUiState(
        componentId = componentId,
        days = props.days.map { it.toAgendaDayUiState(props.calendars, preferences) },
    )

private fun AgendaDay.toAgendaDayUiState(
    calendars: Map<String, Calendar>,
    preferences: ResolvedPreferences,
): AgendaDayUiState =
    AgendaDayUiState(
        date = date,
        headerLabel = headerLabel,
        events = events.map { it.toEventChipUiState(calendars, preferences) },
    )

/**
 * This event as a chip: subtitle lines capped at the resolved density, chip style kept only on the
 * filled-bar components, and the colour looked up from [calendars].
 *
 * The cap is read from `maxSubtitleLines` alone — never inferred from the density level, which the
 * server may remap to a different cap without the level changing. A negative cap, which the
 * contract's `minimum: 0` forbids but decoding does not reject, draws no lines rather than failing
 * the body.
 */
internal fun PresentedCalendarEvent.toEventChipUiState(
    calendars: Map<String, Calendar>,
    preferences: ResolvedPreferences,
): EventChipUiState {
    val content = presentation.toChipContent(preferences.chipStyle)
    val maxSubtitleLines = preferences.chipDensity.maxSubtitleLines.coerceAtLeast(0)
    return EventChipUiState(
        eventId = props.id,
        componentId = presentation.componentId,
        title = content.title,
        leadingIconToken = content.leadingIconToken,
        subtitleLines = content.subtitleLines.take(maxSubtitleLines),
        chipStyle = content.chipStyle,
        calendarColor = calendars[props.calendarId]?.color,
        accessibilityLabel = presentation.accessibilityLabel,
    )
}

/** The display fields every event-presentation component reduces to, before the density cap. */
private data class ChipContent(
    val title: String,
    val leadingIconToken: String?,
    val subtitleLines: List<PresentationLine>,
    val chipStyle: ChipStyle?,
)

private fun EventPresentation.toChipContent(chipStyle: ChipStyle): ChipContent =
    when (this) {
        is MonthAllDayBarPresentation ->
            ChipContent(title, leadingIconToken, subtitleLines, chipStyle = chipStyle)
        is TimeGridAllDayBarPresentation ->
            ChipContent(title, leadingIconToken, subtitleLines, chipStyle = chipStyle)
        is MonthTimedMarkerPresentation ->
            ChipContent(line.text, line.iconToken, subtitleLines = emptyList(), chipStyle = null)
        is EventBlockPresentation ->
            ChipContent(title, leadingIconToken, subtitleLines, chipStyle = null)
        is EventCardPresentation ->
            ChipContent(title, leadingIconToken, subtitleLines, chipStyle = null)
    }
