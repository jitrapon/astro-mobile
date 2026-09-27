package io.jitrapon.astro.presentation.action

import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.data.calendar.OpenUrlAction
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction

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

/**
 * The effect a platform carries out for this action, or `null` for a [SwitchCalendarViewAction],
 * which the observing view model consumes itself and leaves nothing for a platform to do.
 *
 * The one mapping from the contract's action types to client behaviour, so neither the view model
 * nor anything standing in for it restates which action opens what.
 */
fun Action.toActionEffect(): ActionEffect? =
    when (this) {
        is SwitchCalendarViewAction -> null
        is NavigateAction -> ActionEffect.ShowScreen(screen)
        is OpenUrlAction -> ActionEffect.OpenExternalUrl(url)
        is OpenEventDetailAction -> ActionEffect.ShowEventDetail(eventId)
        is PresentModalAction -> ActionEffect.ShowEvents(eventIds)
    }
