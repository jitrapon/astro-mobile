package io.jitrapon.astro.ui.shell

/** Test tags the shell exposes, so a UI test can tell its regions apart from their text. */
object AppShellTestTags {
    const val BOTTOM_BAR = "app_shell_bottom_bar"
    const val LOADING = "app_shell_loading"
    const val FAILURE = "app_shell_failure"
    const val NO_DESTINATIONS = "app_shell_no_destinations"
    const val CALENDAR_TOP_BAR = "app_shell_calendar_top_bar"
    const val CALENDAR_LOADING = "app_shell_calendar_loading"
    const val CALENDAR_FAILURE = "app_shell_calendar_failure"
    const val EVENT_SURFACE = "app_shell_event_surface"

    /** The tag on the bottom-bar tab for the destination whose id is [destinationId]. */
    fun tab(destinationId: String): String = "app_shell_tab_$destinationId"

    /** The tag on the view switcher's option whose id is [optionId]. */
    fun viewSwitcherOption(optionId: String): String = "app_shell_view_option_$optionId"

    /** The tag on the placeholder shown for the destination whose id is [destinationId]. */
    fun destinationPlaceholder(destinationId: String): String =
        "app_shell_destination_$destinationId"
}
