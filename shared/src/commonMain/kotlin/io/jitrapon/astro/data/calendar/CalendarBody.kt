package io.jitrapon.astro.data.calendar

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * The typed body component the server selected for a screen, discriminated on its versioned
 * component name. Each component name is statically bound to its props schema, so a year component
 * carrying month props is not representable.
 *
 * Only the month and agenda components are modelled. The contract also declares
 * `calendar.timeGrid.v1` and `calendar.year.v1`; a response carrying either fails to decode rather
 * than degrading silently, which is the intended signal until those views are built.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("component")
sealed interface CalendarBody

/** Month grid body. */
@Serializable
@SerialName("calendar.month.v1")
data class MonthBody(val props: CalendarMonthViewModel) : CalendarBody

/** Agenda list body. */
@Serializable
@SerialName("calendar.agenda.v1")
data class AgendaBody(val props: CalendarAgendaViewModel) : CalendarBody

/** An inclusive date window: both [start] and [end] are covered by the view. */
@Serializable data class CalendarRange(val start: String, val end: String)

/**
 * Month view-model. The client builds the grid from [range] plus [monthAnchor] plus the user's week
 * start, then buckets [events] by their presentation region. No geometry is carried here.
 */
@Serializable
data class CalendarMonthViewModel(
    val range: CalendarRange,
    /** First day of the month this view is centered on. */
    val monthAnchor: String,
    /** Server-formatted month and year heading. */
    val headerLabel: String,
    /** Calendars referenced by the events, keyed by calendar id. */
    val calendars: Map<String, Calendar>,
    /**
     * Flat and already sorted in a stable display order — all-day bars first, then timed markers,
     * with the event id as the final tiebreak. The client buckets by presentation region; it never
     * re-sorts, because re-sorting loses the tiebreak that keeps optimistic updates stable.
     */
    val events: List<PresentedCalendarEvent>,
)

/** Agenda view-model: events pre-grouped by day, each day carrying a server-formatted header. */
@Serializable
data class CalendarAgendaViewModel(
    val range: CalendarRange,
    val calendars: Map<String, Calendar>,
    val days: List<AgendaDay>,
)

/** One day's section in the agenda list. */
@Serializable
data class AgendaDay(
    val date: String,
    val headerLabel: String,
    /** Server-sorted: all-day first, then by start, then by id. */
    val events: List<PresentedCalendarEvent>,
)
