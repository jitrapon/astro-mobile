package io.jitrapon.astro.ui.component

/**
 * Test tags the calendar component renderers expose, so a UI test can tell which renderer drew a
 * component — a registered one or the fallback — without reading its text.
 */
object CalendarComponentTestTags {

    /** The tag on the month body's affordance presenting the events it does not list. */
    const val MONTH_OVERFLOW = "calendar_month_overflow"

    /** The tag on the renderer registered for the component whose versioned id is [componentId]. */
    fun registered(componentId: String): String = "calendar_component_$componentId"

    /** The tag on the fallback drawn for [componentId], which no renderer is registered for. */
    fun unregistered(componentId: String): String = "calendar_component_unregistered_$componentId"

    /** The tag on each subtitle line drawn for the event whose id is [eventId]. */
    fun subtitleLine(eventId: String): String = "calendar_event_subtitle_line_$eventId"

    /** The tag on the month body's weekday label at [position], 0 being the week's first day. */
    fun weekday(position: Int): String = "calendar_month_weekday_$position"
}
