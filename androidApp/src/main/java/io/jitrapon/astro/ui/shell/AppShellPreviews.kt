package io.jitrapon.astro.ui.shell

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

@Preview(showBackground = true)
@Composable
internal fun AppShellFailurePreview() {
    AstroTheme {
        AppShell(state = AppShellState.Failed(IllegalStateException("No backend reachable")))
    }
}
