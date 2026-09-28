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
 * The effect a platform carries out for this action, or `null` when there is none to carry out: a
 * [SwitchCalendarViewAction], which the observing view model consumes itself, or an [OpenUrlAction]
 * whose URL this client will not hand outside the app.
 *
 * The contract constrains a URL only to URI syntax, so a delivered one may name any scheme. Only
 * `http` and `https` leave the app. Anything else is refused here, once for both platforms: a
 * `file:` URI makes Android's launch throw `FileUriExposedException` — a crash, not the "no app can
 * open this" path — and a scheme such as `intent:` or `javascript:` has no business being launched
 * on a server's say-so.
 *
 * The one mapping from the contract's action types to client behaviour, so neither the view model
 * nor anything standing in for it restates which action opens what.
 */
fun Action.toActionEffect(): ActionEffect? =
    when (this) {
        is SwitchCalendarViewAction -> null
        is NavigateAction -> ActionEffect.ShowScreen(screen)
        is OpenUrlAction -> if (url.hasOpenableScheme()) ActionEffect.OpenExternalUrl(url) else null
        is OpenEventDetailAction -> ActionEffect.ShowEventDetail(eventId)
        is PresentModalAction -> ActionEffect.ShowEvents(eventIds)
    }

/** The URL schemes an [OpenUrlAction] may hand outside the app. */
private val OPENABLE_URL_SCHEMES = setOf("http", "https")

/** Whether this URL's scheme is one of [OPENABLE_URL_SCHEMES], compared case-insensitively. */
private fun String.hasOpenableScheme(): Boolean =
    substringBefore(':', missingDelimiterValue = "").lowercase() in OPENABLE_URL_SCHEMES
