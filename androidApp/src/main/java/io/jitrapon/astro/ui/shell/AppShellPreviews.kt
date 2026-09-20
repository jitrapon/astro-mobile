package io.jitrapon.astro.ui.shell

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.AppShellTab
import io.jitrapon.astro.ui.main.theme.AstroTheme

/** Tabs shaped like the contract's example screen: two destinations, each routing to a screen. */
private val PreviewTabs =
    listOf(
        AppShellTab(
            destinationId = "calendar",
            label = "Calendar",
            iconToken = "icon.calendar",
            targetScreenId = "calendar",
        ),
        AppShellTab(
            destinationId = "expense",
            label = "Expense",
            iconToken = "icon.wallet",
            targetScreenId = "expense",
        ),
    )

@Preview(showBackground = true)
@Composable
internal fun AppShellTabsPreview() {
    AstroTheme { AppShell(state = AppShellState.Tabs(PreviewTabs)) }
}

@Preview(showBackground = true)
@Composable
internal fun AppShellLoadingPreview() {
    AstroTheme { AppShell(state = AppShellState.Loading) }
}

/**
 * The failure message in dark mode, where it is the easiest of the shell's states to get wrong: it
 * draws outside the scaffold, so without a backdrop supplying a background and the content colour
 * that belongs with it, the text keeps Material's default black and vanishes.
 */
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun AppShellFailureNightPreview() {
    AstroTheme(darkTheme = true) {
        AppShell(state = AppShellState.Failed(IllegalStateException("No backend reachable")))
    }
}

@Preview(showBackground = true)
@Composable
internal fun AppShellNoDestinationsPreview() {
    AstroTheme { AppShell(state = AppShellState.NoDestinations) }
}

@Preview(showBackground = true)
@Composable
internal fun AppShellFailurePreview() {
    AstroTheme {
        AppShell(state = AppShellState.Failed(IllegalStateException("No backend reachable")))
    }
}
