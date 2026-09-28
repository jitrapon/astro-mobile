package io.jitrapon.astro.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationDefaults
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.AppShellTab
import kotlinx.coroutines.flow.StateFlow

/**
 * The app shell fed by streams of state: collects [shellState] and [calendarState] for as long as
 * the host is at least started, and draws them with [AppShell].
 *
 * Takes the streams rather than the view model that publishes them, so a test or a preview can
 * drive the shell with any flow of states.
 */
@Composable
fun AppShellRoute(
    shellState: StateFlow<AppShellState>,
    calendarState: StateFlow<CalendarUiState>,
    interactions: AppShellInteractions,
    modifier: Modifier = Modifier,
) {
    val state by shellState.collectAsStateWithLifecycle()
    val calendar by calendarState.collectAsStateWithLifecycle()
    AppShell(state = state, calendar = calendar, interactions = interactions, modifier = modifier)
}

/**
 * The shell around every screen: a bottom bar with one tab per destination the server delivered,
 * and the selected destination's screen above it — or, before any destinations have arrived, a
 * loading or failure placeholder with no bar at all.
 *
 * [calendar] is the one screen this app observes. A tab whose target is that screen draws it — its
 * title and view switcher in a top bar, its body below.
 */
@Composable
fun AppShell(
    state: AppShellState,
    calendar: CalendarUiState,
    interactions: AppShellInteractions,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AppShellState.Loading -> LoadingPlaceholder(modifier)
        AppShellState.NoDestinations -> NoDestinationsPlaceholder(modifier)
        is AppShellState.Failed -> FailurePlaceholder(modifier)
        is AppShellState.Tabs ->
            TabbedShell(
                tabs = state.tabs,
                calendar = calendar,
                interactions = interactions,
                modifier = modifier,
            )
    }
}

/**
 * The bottom bar and the navigation display for [tabs], which is never empty.
 *
 * Selecting a tab dispatches its action, and the shell carries out the effect that comes back:
 * showing a screen selects the destination that targets it, opening a URL hands it to the platform,
 * and the event effects open the not-yet-built event surface. A tab whose action does not navigate
 * therefore never becomes the selection and holds no back stack — it acts, and the current screen
 * stays in place.
 *
 * Which destination is selected, and how each effect changes it, is [ShellSelectionState]'s; the
 * back stack derived from it is [ShellScreens']. With no navigating tab at all there is no screen
 * to show, only the bar.
 *
 * The selected tab shows the calendar screen when its target is that screen's id, and the calendar
 * top bar appears only then — a title and switcher belong to that screen, not to the shell.
 *
 * The window draws edge to edge, so the bars and the content between them take their insets
 * explicitly: the default [BottomNavigation], [TopAppBar] and [Scaffold] overloads apply none,
 * which would leave the tabs under the system navigation bar with their tap targets behind the
 * system's own controls.
 */
@Composable
private fun TabbedShell(
    tabs: List<AppShellTab>,
    calendar: CalendarUiState,
    interactions: AppShellInteractions,
    modifier: Modifier = Modifier,
) {
    val selection = rememberShellSelectionState()
    val navigatingTabs = tabs.filter { it.targetScreenId != null }
    val rootTab = navigatingTabs.firstOrNull()
    val selectedTab = selection.selectedTab(navigatingTabs)

    // Every affordance in the shell dispatches an action; [source] is the tab it came from, which a
    // screen effect prefers when choosing the destination to show.
    val act = { action: Action, source: AppShellTab? ->
        selection.carryOut(
            effect = interactions.dispatch(action),
            source = source,
            navigatingTabs = navigatingTabs,
            openExternalUrl = interactions.openExternalUrl,
        )
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        modifier = modifier,
        topBar = {
            if (selectedTab?.showsScreenOf(calendar) == true) {
                CalendarTopBar(
                    title = calendar.title.orEmpty(),
                    viewSwitcher = calendar.viewSwitcher,
                    requestStatus = calendar.requestStatus(),
                    onViewSelected = { option -> act(option.action, selectedTab) },
                )
            }
        },
        bottomBar = {
            ShellBottomBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { tab -> act(tab.action, tab) },
            )
        },
    ) { contentPadding ->
        if (rootTab != null && selectedTab != null) {
            ShellScreens(
                backStack = backStackOf(rootTab, selectedTab),
                calendar = calendar,
                onAction = { action -> act(action, selectedTab) },
                onBack = { selection.select(rootTab) },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    selection.shownEventEffect?.let { effect ->
        EventSurfacePlaceholder(effect = effect, onDismiss = selection::dismissEventSurface)
    }
}

/**
 * The back stack for [selectedTab], derived from the selection rather than kept alongside it:
 * [rootTab] is its root, and any other selected tab sits above it. Back from another tab therefore
 * returns to the root, and back from the root leaves the app.
 */
private fun backStackOf(rootTab: AppShellTab, selectedTab: AppShellTab): List<AppShellTab> =
    if (selectedTab == rootTab) listOf(rootTab) else listOf(rootTab, selectedTab)

/**
 * The navigation display for [backStack]'s top destination. Entries are keyed by destination id, so
 * two destinations that route to the same screen still get separate entries and saved state.
 */
@Composable
private fun ShellScreens(
    backStack: List<AppShellTab>,
    calendar: CalendarUiState,
    onAction: (Action) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryProvider = { tab ->
            NavEntry(key = tab, contentKey = tab.destinationId) {
                val body = calendar.body
                if (body != null && tab.showsScreenOf(calendar)) {
                    CalendarScreenBody(body, onAction)
                } else {
                    DestinationPlaceholder(tab)
                }
            }
        },
    )
}

/** Whether this tab's target is the screen [calendar] carries. */
private fun AppShellTab.showsScreenOf(calendar: CalendarUiState): Boolean {
    val screenId = calendar.content?.screen?.id
    return screenId != null && targetScreenId == screenId
}

@Composable
private fun ShellBottomBar(
    tabs: List<AppShellTab>,
    selectedTab: AppShellTab?,
    onTabSelected: (AppShellTab) -> Unit,
) {
    BottomNavigation(
        windowInsets = BottomNavigationDefaults.windowInsets,
        modifier = Modifier.testTag(AppShellTestTags.BOTTOM_BAR),
    ) {
        tabs.forEach { tab ->
            BottomNavigationItem(
                selected = tab.destinationId == selectedTab?.destinationId,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.testTag(AppShellTestTags.tab(tab.destinationId)),
                icon = { Icon(imageVector = tab.iconToken.toTabIcon(), contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

/**
 * The icon for a destination's semantic icon token. Tokens this app does not know — including ones
 * the server adds later — fall back to a generic icon rather than leaving the tab blank.
 */
private fun String?.toTabIcon(): ImageVector =
    when (this) {
        "icon.calendar" -> Icons.Filled.DateRange
        "icon.wallet" -> Icons.Filled.ShoppingCart
        else -> Icons.Filled.Star
    }
