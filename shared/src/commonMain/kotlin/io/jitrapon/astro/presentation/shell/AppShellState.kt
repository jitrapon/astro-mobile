package io.jitrapon.astro.presentation.shell

import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.NavDestination
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.presentation.calendar.CalendarUiState

/**
 * What the app shell around every screen shows: nothing yet, a reason nothing could be shown,
 * nowhere to go, or a bottom bar of tabs.
 *
 * The tabs come only from the destinations a delivered screen carries — never from a list either
 * app keeps — so the product's information architecture stays a server decision. Destinations
 * arrive inside a screen response, which is why there are no tabs to show before one has loaded.
 */
sealed interface AppShellState {

    /** No screen has offered any destinations yet, and nothing has reported why not. */
    data object Loading : AppShellState

    /**
     * A screen arrived carrying no destinations at all, with no exchange still running behind it.
     *
     * Distinct from [Loading] because it is settled rather than pending: nothing further is on its
     * way, so a shell that showed progress here would show it forever. The contract permits the
     * response — `destinations` is required but has no minimum length — so this is a state to state
     * plainly, not one to treat as impossible.
     */
    data object NoDestinations : AppShellState

    /** No screen with destinations could be shown, because the most recent exchange failed. */
    data class Failed(
        /**
         * What the exchange failed with — the exception rather than a message, since the text a
         * person reads needs the platform's localisation.
         */
        val failure: Exception
    ) : AppShellState

    /** A screen offered destinations, and each one is a tab. Never empty. */
    data class Tabs(
        /** One tab per delivered destination id, in the order the contract delivered them. */
        val tabs: List<AppShellTab>
    ) : AppShellState
}

/**
 * One bottom-navigation tab, reduced to plain values a renderer on either platform can draw, plus
 * the action selecting it dispatches.
 */
data class AppShellTab(
    /**
     * The destination's identity, and the key a renderer holds tab selection and navigation state
     * under. Unique within [AppShellState.Tabs]; two destinations may still target the same screen,
     * so [targetScreenId] is never a substitute for it.
     */
    val destinationId: String,
    /** The label to show, already formatted by the server for the response's locale. */
    val label: String,
    /**
     * The semantic icon the destination asks for, or `null` when it names none. A token rather than
     * an image: each platform maps it to its own icon set, and falls back for tokens it does not
     * recognise.
     */
    val iconToken: String?,
    /**
     * What selecting this tab does, exactly as delivered. Any of the contract's action types — a
     * tab may open a URL or switch the calendar view as well as navigate — so a renderer dispatches
     * it rather than assuming navigation.
     */
    val action: Action,
) {
    /**
     * The product screen selecting this tab shows, or `null` when its [action] does not navigate.
     * Only a tab with a target screen holds a back stack; any other tab performs its action and
     * leaves the current screen in place.
     */
    val targetScreenId: String?
        get() = (action as? NavigateAction)?.screen
}

/**
 * Projects a calendar screen's renderer state onto the shell drawn around it.
 *
 * The only place the rules are stated, so neither app re-derives them:
 * - **Tabs win.** Content that yields at least one tab projects to [AppShellState.Tabs] whatever
 *   the loading flag and failure beside it say, so a refresh — or a failed refresh — over a loaded
 *   screen keeps its bottom bar rather than blanking the shell.
 * - **Contract order is kept.** Tabs appear in the order the destinations were delivered.
 * - **Every destination is a tab, whatever its action.** The contract attaches an action to a
 *   destination and nothing else in the shell, so dropping one whose action does not navigate would
 *   leave that action type with no affordance at all. The tab carries the action; only a tab whose
 *   action is [NavigateAction] has a [target screen][AppShellTab.targetScreenId] and a back stack.
 * - **A repeated destination id keeps its first occurrence.** Tab identity keys navigation state,
 *   so two tabs sharing an id would share — and corrupt — one back stack.
 * - **No tabs means no bar.** When nothing yields a tab, a reported failure projects to
 *   [AppShellState.Failed]. Otherwise a screen that has already arrived carrying no destinations,
 *   with no exchange still running behind it, projects to [AppShellState.NoDestinations] — it is
 *   settled, and showing progress over it would show progress forever. Everything else, whether
 *   nothing has been delivered yet or an exchange is still in flight, is [AppShellState.Loading].
 */
fun CalendarUiState.toAppShellState(): AppShellState {
    val tabs = content?.screen?.navigation?.destinations.orEmpty().toAppShellTabs()
    val reportedFailure = failure
    return when {
        tabs.isNotEmpty() -> AppShellState.Tabs(tabs)
        reportedFailure != null -> AppShellState.Failed(reportedFailure)
        content != null && !isLoading -> AppShellState.NoDestinations
        else -> AppShellState.Loading
    }
}

/** Every destination as a tab, first occurrence of each id, in delivered order. */
private fun List<NavDestination>.toAppShellTabs(): List<AppShellTab> = distinctBy {
    it.id
}
    .map { it.toAppShellTab() }

private fun NavDestination.toAppShellTab(): AppShellTab =
    AppShellTab(destinationId = id, label = label, iconToken = iconToken, action = action)
