package io.jitrapon.astro.presentation.shell

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
     * A screen arrived and carried nothing this app can route to, with no exchange still running
     * behind it.
     *
     * Distinct from [Loading] because it is settled rather than pending: nothing further is on its
     * way, so a shell that showed progress here would show it forever. The contract permits the
     * response — `destinations` is required but has no minimum length, and a destination may carry
     * an action that does not navigate — so this is a state to state plainly, not one to treat as
     * impossible.
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
        /** One tab per routable destination, in the order the contract delivered them. */
        val tabs: List<AppShellTab>
    ) : AppShellState
}

/**
 * One bottom-navigation tab, reduced to plain values a renderer on either platform can draw and
 * route with.
 */
data class AppShellTab(
    /**
     * The destination's identity, and the key a renderer holds tab selection and navigation state
     * under. Unique within [AppShellState.Tabs]; two destinations may still share a
     * [targetScreenId], so the screen id is never a substitute for it.
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
    /** The product screen selecting this tab navigates to. */
    val targetScreenId: String,
)

/**
 * Projects a calendar screen's renderer state onto the shell drawn around it.
 *
 * The only place the rules are stated, so neither app re-derives them:
 * - **Tabs win.** Content that yields at least one tab projects to [AppShellState.Tabs] whatever
 *   the loading flag and failure beside it say, so a refresh — or a failed refresh — over a loaded
 *   screen keeps its bottom bar rather than blanking the shell.
 * - **Contract order is kept.** Tabs appear in the order the destinations were delivered.
 * - **Only navigating destinations become tabs.** A tab must route to a screen; a destination whose
 *   action is anything other than [NavigateAction] is dropped rather than drawn as a tab that
 *   cannot go anywhere.
 * - **A repeated destination id keeps its first occurrence.** Tab identity keys navigation state,
 *   so two tabs sharing an id would share — and corrupt — one back stack.
 * - **No tabs means no bar.** When nothing yields a tab, a reported failure projects to
 *   [AppShellState.Failed]. Otherwise a screen that has already arrived carrying nothing routable,
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

/** Keeps the routable destinations, first occurrence of each id, in delivered order, as tabs. */
private fun List<NavDestination>.toAppShellTabs(): List<AppShellTab> {
    val routable = mapNotNull { it.toAppShellTabOrNull() }
    return routable.distinctBy { it.destinationId }
}

/** This destination as a tab, or `null` when its action does not navigate to a screen. */
private fun NavDestination.toAppShellTabOrNull(): AppShellTab? {
    val navigate = action as? NavigateAction ?: return null
    return AppShellTab(
        destinationId = id,
        label = label,
        iconToken = iconToken,
        targetScreenId = navigate.screen,
    )
}
