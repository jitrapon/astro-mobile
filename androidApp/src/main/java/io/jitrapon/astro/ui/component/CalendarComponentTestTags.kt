package io.jitrapon.astro.ui.component

/**
 * Test tags the calendar component renderers expose, so a UI test can tell which renderer drew a
 * component — a registered one or the fallback — without reading its text.
 */
object CalendarComponentTestTags {

    /** The tag on the renderer registered for the component whose versioned id is [componentId]. */
    fun registered(componentId: String): String = "calendar_component_$componentId"

    /** The tag on the fallback drawn for [componentId], which no renderer is registered for. */
    fun unregistered(componentId: String): String = "calendar_component_unregistered_$componentId"
}
