package io.jitrapon.astro.presentation.action

/**
 * What a platform does in response to a dispatched action that the shared layer cannot carry out
 * itself — each case names the client's behaviour, not the wire type that asked for it.
 *
 * Switching the calendar view has no case here: it only re-points the screen being observed, which
 * the view model does on its own, so the platform has nothing to execute and learns of the switch
 * through the next delivered state.
 */
sealed interface ActionEffect {

    /** Show the product screen named by [screenId]. */
    data class ShowScreen(val screenId: String) : ActionEffect

    /** Hand [url] to the platform to open outside the app. */
    data class OpenExternalUrl(val url: String) : ActionEffect

    /** Show the detail surface for the one event named by [eventId]. */
    data class ShowEventDetail(val eventId: String) : ActionEffect

    /**
     * Present the events named by [eventIds] over the current screen — the ones an overflow hides,
     * or a day's events — in the order delivered.
     */
    data class ShowEvents(val eventIds: List<String>) : ActionEffect
}
