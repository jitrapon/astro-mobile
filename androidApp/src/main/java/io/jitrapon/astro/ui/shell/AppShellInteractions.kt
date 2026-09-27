package io.jitrapon.astro.ui.shell

import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.presentation.action.ActionEffect

/**
 * How the shell acts on what the person does, and the one platform service it needs to finish.
 *
 * Every affordance inside the shell — a tab, a view option, an event on the screen — produces an
 * [Action], so one [dispatch] serves them all and the shell carries out whatever effect comes back.
 * Grouped so a test or preview supplies both in one place.
 */
class AppShellInteractions(
    /**
     * Acts on the calendar screen and returns the effect the shell must carry out, or `null` when
     * the screen consumed the action itself — as a view switch is.
     */
    val dispatch: (Action) -> ActionEffect?,
    /** Opens a URL the server delivered outside the app. */
    val openExternalUrl: (String) -> Unit,
)
