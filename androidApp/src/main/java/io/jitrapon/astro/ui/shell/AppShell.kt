package io.jitrapon.astro.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationDefaults
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.jitrapon.astro.R
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.AppShellTab
import kotlinx.coroutines.flow.StateFlow

/**
 * The app shell fed by a stream of shell states: collects [shellState] for as long as the host is
 * at least started, and draws each state with [AppShell].
 *
 * Takes the stream rather than the view model that publishes it, so a test or a preview can drive
 * the shell with any flow of states.
 */
@Composable
fun AppShellRoute(shellState: StateFlow<AppShellState>, modifier: Modifier = Modifier) {
    val state by shellState.collectAsStateWithLifecycle()
    AppShell(state = state, modifier = modifier)
}

/**
 * The shell around every screen: a bottom bar with one tab per destination the server delivered,
 * and the selected destination's screen above it — or, before any destinations have arrived, a
 * loading or failure placeholder with no bar at all.
 */
@Composable
fun AppShell(state: AppShellState, modifier: Modifier = Modifier) {
    when (state) {
        AppShellState.Loading -> LoadingPlaceholder(modifier)
        is AppShellState.Failed -> FailurePlaceholder(modifier)
        is AppShellState.Tabs -> TabbedShell(tabs = state.tabs, modifier = modifier)
    }
}

/**
 * The bottom bar and the navigation display for [tabs], which is never empty.
 *
 * Selection is held by destination id, the tab's identity, and survives recreation. When a refresh
 * delivers a set of destinations that no longer contains the selected one, the first tab is shown
 * instead.
 *
 * The back stack is derived from the selection rather than kept alongside it: the first tab is its
 * root, and any other selected tab sits above it. Back from another tab therefore returns to the
 * first, and back from the first leaves the app. Entries are keyed by destination id, so two
 * destinations that route to the same screen still get separate entries and separate saved state.
 *
 * The window draws edge to edge, so the bar and the content below it take their insets explicitly:
 * the default [BottomNavigation] and [Scaffold] overloads apply none, which would leave the tabs
 * under the system navigation bar with their tap targets behind the system's own controls.
 */
@Composable
private fun TabbedShell(tabs: List<AppShellTab>, modifier: Modifier = Modifier) {
    var selectedDestinationId by rememberSaveable { mutableStateOf<String?>(null) }
    val startTab = tabs.first()
    val selectedTab = tabs.firstOrNull { it.destinationId == selectedDestinationId } ?: startTab
    val backStack = if (selectedTab == startTab) listOf(startTab) else listOf(startTab, selectedTab)

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        modifier = modifier,
        bottomBar = {
            ShellBottomBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedDestinationId = it.destinationId },
            )
        },
    ) { contentPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(contentPadding),
            onBack = { selectedDestinationId = startTab.destinationId },
            entryProvider = { tab ->
                NavEntry(key = tab, contentKey = tab.destinationId) { DestinationPlaceholder(tab) }
            },
        )
    }
}

@Composable
private fun ShellBottomBar(
    tabs: List<AppShellTab>,
    selectedTab: AppShellTab,
    onTabSelected: (AppShellTab) -> Unit,
) {
    BottomNavigation(
        windowInsets = BottomNavigationDefaults.windowInsets,
        modifier = Modifier.testTag(AppShellTestTags.BOTTOM_BAR),
    ) {
        tabs.forEach { tab ->
            BottomNavigationItem(
                selected = tab.destinationId == selectedTab.destinationId,
                onClick = { onTabSelected(tab) },
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

/** What stands in for a destination's screen until that screen is built. */
@Composable
private fun DestinationPlaceholder(tab: AppShellTab) {
    CenteredMessage(
        text = tab.label,
        modifier = Modifier.testTag(AppShellTestTags.destinationPlaceholder(tab.destinationId)),
    )
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    CenteredMessage(
        text = stringResource(R.string.app_shell_loading),
        modifier = modifier.testTag(AppShellTestTags.LOADING),
        leading = { CircularProgressIndicator() },
    )
}

@Composable
private fun FailurePlaceholder(modifier: Modifier = Modifier) {
    CenteredMessage(
        text = stringResource(R.string.app_shell_failure),
        modifier = modifier.testTag(AppShellTestTags.FAILURE),
    )
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leading?.invoke()
            Text(text = text, style = MaterialTheme.typography.h6)
        }
    }
}
