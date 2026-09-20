package io.jitrapon.astro.ui.shell

/** Test tags the shell exposes, so a UI test can tell its regions apart from their text. */
object AppShellTestTags {
    const val BOTTOM_BAR = "app_shell_bottom_bar"
    const val LOADING = "app_shell_loading"
    const val FAILURE = "app_shell_failure"
    const val NO_DESTINATIONS = "app_shell_no_destinations"

    /** The tag on the placeholder shown for the destination whose id is [destinationId]. */
    fun destinationPlaceholder(destinationId: String): String =
        "app_shell_destination_$destinationId"
}
