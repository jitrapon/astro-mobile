package io.jitrapon.astro.ui.shell

import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.calendar.ViewSwitcherOptionUiState
import io.jitrapon.astro.presentation.shell.AppShellTab

/**
 * What the shell reports the person doing, and the one platform service it needs to answer them.
 *
 * Grouped rather than passed singly so the shell's entry points stay readable as the screens inside
 * it gain affordances, and so a test or preview supplies them in one place.
 */
class AppShellInteractions(
    /**
     * Dispatches the tab's action and returns the effect the shell must carry out, or `null` when
     * the screen consumed the action itself.
     */
    val onTabSelected: (AppShellTab) -> ActionEffect?,
    /** Switches the calendar to the chosen view. */
    val onCalendarViewSelected: (ViewSwitcherOptionUiState) -> Unit,
    /** Opens [String] — a URL the server delivered — outside the app. */
    val onOpenExternalUrl: (String) -> Unit,
)
