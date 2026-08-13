package io.jitrapon.astro.data.calendar

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** One server-composed, locale- and time-zone-formatted line of display text. */
@Serializable data class PresentationLine(val text: String, val iconToken: String? = null)

/**
 * Where in a view an event is placed. The server chooses the region as policy, so it may diverge
 * from the event's `kind` — a long timed event can be placed in an all-day band. Clients bucket
 * events by this value; they never re-derive it from the event.
 */
@Serializable
enum class EventRegion {
    @SerialName("monthSpanBand") MONTH_SPAN_BAND,
    @SerialName("monthCell") MONTH_CELL,
    @SerialName("timeGridAllDayBand") TIME_GRID_ALL_DAY_BAND,
    @SerialName("timeGridBody") TIME_GRID_BODY,
    @SerialName("agendaList") AGENDA_LIST,
}

/**
 * The versioned UI component and the display content for rendering one event in one view.
 *
 * Each `component` binds to exactly one [region], so an invalid component-by-region combination is
 * not representable. Components are view-specific and version on their own clock, independently of
 * the data-axis `itemType` on [PresentedCalendarEvent].
 *
 * No geometry crosses this boundary: sizing, lane-packing, week-boundary segmentation and overflow
 * slicing are client-owned.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("component")
sealed interface EventPresentation {
    val region: EventRegion
    val accessibilityLabel: String?
}

/**
 * Month all-day representation: one filled bar spanning 1..N day cells — the same component for
 * single- and multi-day events. The client computes the covered cells, lane-packs, and segments at
 * week boundaries from the event's dates plus the user's week start. Filled per
 * [ResolvedPreferences.chipStyle].
 */
@Serializable
@SerialName("calendar.event.monthAllDayBar.v1")
data class MonthAllDayBarPresentation(
    override val region: EventRegion,
    val title: String,
    /** Ordered; the client renders the first [ChipDensity.maxSubtitleLines] of them. */
    val subtitleLines: List<PresentationLine> = emptyList(),
    val leadingIconToken: String? = null,
    override val accessibilityLabel: String? = null,
    val styleVariant: String? = null,
    val styleToken: String? = null,
) : EventPresentation

/**
 * Month timed representation: a leading calendar-color dot then a single combined content line
 * (time + title, composed by the server). Carries no `title` of its own — the text IS [line].
 *
 * The dot color is resolved client-side from `calendars[props.calendarId].color.accentColor`; no
 * color is denormalized onto the presentation. Chip style does not apply (there is no fill).
 */
@Serializable
@SerialName("calendar.event.monthTimedMarker.v1")
data class MonthTimedMarkerPresentation(
    override val region: EventRegion,
    val line: PresentationLine,
    override val accessibilityLabel: String? = null,
) : EventPresentation

/**
 * Time-grid all-day representation: a column-spanning bar in the band above the grid. The client
 * lane-packs and segments at column boundaries.
 */
@Serializable
@SerialName("calendar.event.timeGridAllDayBar.v1")
data class TimeGridAllDayBarPresentation(
    override val region: EventRegion,
    val title: String,
    val subtitleLines: List<PresentationLine> = emptyList(),
    val leadingIconToken: String? = null,
    override val accessibilityLabel: String? = null,
    val styleVariant: String? = null,
    val styleToken: String? = null,
) : EventPresentation

/**
 * Time-positioned block in the time-grid body. The client derives vertical position and height from
 * the event's start and end instants.
 */
@Serializable
@SerialName("calendar.event.block.v1")
data class EventBlockPresentation(
    override val region: EventRegion,
    val title: String,
    val subtitleLines: List<PresentationLine> = emptyList(),
    val leadingIconToken: String? = null,
    override val accessibilityLabel: String? = null,
    val styleVariant: String? = null,
    val styleToken: String? = null,
) : EventPresentation

/** Agenda list row. */
@Serializable
@SerialName("calendar.event.card.v1")
data class EventCardPresentation(
    override val region: EventRegion,
    val title: String,
    val subtitleLines: List<PresentationLine> = emptyList(),
    val leadingIconToken: String? = null,
    override val accessibilityLabel: String? = null,
    val styleVariant: String? = null,
    val styleToken: String? = null,
) : EventPresentation

/**
 * The data axis of a view-model item, kept independent of the UI axis on [EventPresentation].
 *
 * A single member today; the contract reserves it for a future item union (tasks, reminders), which
 * is why the view-models' item arrays carry it explicitly rather than assuming events.
 */
@Serializable
enum class CalendarItemType {
    @SerialName("calendar.event.v1") CALENDAR_EVENT_V1
}

/**
 * A canonical event placed in a view: what kind of item it is, how to render it, and the event
 * itself. The two discriminators are independent — [itemType] selects the shape of [props],
 * [presentation]'s component selects the UI component.
 */
@Serializable
data class PresentedCalendarEvent(
    val itemType: CalendarItemType,
    val presentation: EventPresentation,
    val props: CalendarEvent,
)
