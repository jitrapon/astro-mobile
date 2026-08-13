package io.jitrapon.astro.data.calendar

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** A user's calendar (provider link). Referenced from an event by `calendarId`. */
@Serializable data class Calendar(val id: String, val displayName: String, val color: CalendarColor)

/**
 * Per-calendar color triple, each slot carrying a semantic token name and a resolved fallback
 * value: accent = edge, background = low-opacity fill, foreground = text (contrast-checked against
 * the background).
 */
@Serializable
data class CalendarColor(
    val accentToken: String,
    val accentColor: String,
    val backgroundToken: String,
    val backgroundColor: String,
    val foregroundToken: String,
    val foregroundColor: String,
)

/** What the signed-in user may do to an event. */
@Serializable
data class EventPermissions(val canEdit: Boolean, val canDelete: Boolean, val canMove: Boolean)

/**
 * The canonical event: identity, interaction, and temporal core, discriminated on `kind`.
 *
 * Display strings (title, subtitle lines, icons, accessibility label) live in the wrapping
 * [EventPresentation], not here — this object is exactly the mutation/concurrency payload.
 *
 * The two branches carry different temporal shapes, so an event's `kind` cannot disagree with its
 * dates: a timed event has instants, an all-day event has floating dates.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface CalendarEvent {
    val id: String

    /** References a key in the view-model's `calendars` map. */
    val calendarId: String

    val permissions: EventPermissions

    /** Entity version/etag for optimistic concurrency (`If-Match`). */
    val version: String
}

/** An event pinned to time-zone-aware instants. */
@Serializable
@SerialName("timed")
data class TimedEvent(
    override val id: String,
    override val calendarId: String,
    override val permissions: EventPermissions,
    override val version: String,
    val startAt: String,
    val endAt: String,
) : CalendarEvent

/** An event pinned to floating calendar dates (no time, no zone). */
@Serializable
@SerialName("allDay")
data class AllDayEvent(
    override val id: String,
    override val calendarId: String,
    override val permissions: EventPermissions,
    override val version: String,
    val startDate: String,
    /**
     * The INCLUSIVE last day the event covers — a single-day all-day event has `startDate ==
     * endDate`. Google's exclusive `end.date` convention is converted on the server; treating this
     * as exclusive shortens every span by a day.
     */
    val endDate: String,
) : CalendarEvent
