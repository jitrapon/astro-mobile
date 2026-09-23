package io.jitrapon.astro.data.calendar

/**
 * The versioned wire names of the server-driven calendar components this client models — the
 * `component` discriminator values of [CalendarBody] and [EventPresentation].
 *
 * One source for two readers: each branch's `@SerialName` names its constant here, and each
 * platform's component registry keys its renderers on the same constants through the branch's
 * `componentId`. A registry therefore cannot key on a spelling the decoder never produces, and a
 * version bump is one edit that moves the discriminator and every registry lookup together.
 *
 * Only modelled components are listed. The contract also declares the time-grid and year bodies,
 * which fail to decode until they are built (see [CalendarBody]), so no registry can be handed one.
 */
object CalendarComponentIds {
    const val MONTH_BODY: String = "calendar.month.v1"
    const val AGENDA_BODY: String = "calendar.agenda.v1"

    const val MONTH_ALL_DAY_BAR: String = "calendar.event.monthAllDayBar.v1"
    const val MONTH_TIMED_MARKER: String = "calendar.event.monthTimedMarker.v1"
    const val TIME_GRID_ALL_DAY_BAR: String = "calendar.event.timeGridAllDayBar.v1"
    const val EVENT_BLOCK: String = "calendar.event.block.v1"
    const val EVENT_CARD: String = "calendar.event.card.v1"
}
