package io.jitrapon.astro.ui.shell

import android.view.ViewGroup
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.jitrapon.astro.presentation.shell.AppShellState
import io.jitrapon.astro.presentation.shell.AppShellTab
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.main.theme.AstroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AppShellTest {

    // Hosted in the app's own activity rather than `createComposeRule()`'s bare ComponentActivity.
    // That activity reaches the app's manifest only through `ui-test-manifest`, a debug-only
    // artifact, and instrumented tests run against the release variant — so it would not resolve.
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loadingShowsNoBarUntilTabsArriveThenEachTabShowsItsOwnPlaceholder() {
        val shellState = MutableStateFlow<AppShellState>(AppShellState.Loading)
        // The loading placeholder's progress indicator animates forever, so with the clock
        // auto-advancing the UI never settles and every idle wait runs to its timeout. Hold the
        // clock while loading is on screen; the tabs that replace it have no endless animation.
        composeRule.mainClock.autoAdvance = false
        showShell(shellState)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(AppShellTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(AppShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        shellState.value = AppShellState.Tabs(FIXTURE_TABS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AppShellTestTags.LOADING).assertDoesNotExist()
        composeRule.onNodeWithTag(AppShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.assertTabLabelsInOrder(FIXTURE_TABS.map { it.label })
        composeRule.assertOnlyPlaceholderShownIs(FIXTURE_TABS.first(), FIXTURE_TABS)

        FIXTURE_TABS.forEach { tab ->
            composeRule.tabItem(tab.label).performClick()
            composeRule.waitForIdle()
            composeRule.assertOnlyPlaceholderShownIs(tab, FIXTURE_TABS)
        }
    }

    @Test
    fun destinationsTargetingTheSameScreenKeepSeparatePlaceholdersAndSelection() {
        val primary =
            AppShellTab(
                destinationId = "calendar",
                label = "Calendar",
                iconToken = "icon.calendar",
                targetScreenId = "calendar",
            )
        val secondary =
            AppShellTab(
                destinationId = "shared-calendar",
                label = "Shared calendar",
                iconToken = null,
                targetScreenId = "calendar",
            )
        val tabs = listOf(primary, secondary)
        showShell(MutableStateFlow(AppShellState.Tabs(tabs)))

        composeRule.tabItem(secondary.label).performClick()
        composeRule.waitForIdle()
        composeRule.tabItem(secondary.label).assertIsSelected()
        composeRule.tabItem(primary.label).assertIsNotSelected()
        composeRule.assertOnlyPlaceholderShownIs(secondary, tabs)

        composeRule.tabItem(primary.label).performClick()
        composeRule.waitForIdle()
        composeRule.tabItem(primary.label).assertIsSelected()
        composeRule.tabItem(secondary.label).assertIsNotSelected()
        composeRule.assertOnlyPlaceholderShownIs(primary, tabs)
    }

    @Test
    fun failureShowsTheFailurePlaceholderWithNoBar() {
        showShell(MutableStateFlow(AppShellState.Failed(IllegalStateException("unreachable"))))

        composeRule.onNodeWithTag(AppShellTestTags.FAILURE).assertIsDisplayed()
        composeRule.onNodeWithTag(AppShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }

    @Test
    fun noDestinationsShowsATerminalPlaceholderRatherThanProgress() {
        showShell(MutableStateFlow(AppShellState.NoDestinations))

        composeRule.onNodeWithTag(AppShellTestTags.NO_DESTINATIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(AppShellTestTags.LOADING).assertDoesNotExist()
        composeRule.onNodeWithTag(AppShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }

    private fun showShell(shellState: MutableStateFlow<AppShellState>) {
        // MainActivity composes the live shell in `onCreate`, and the rule refuses to set content
        // over an activity that already has some. Detach it first: its composition is disposed
        // with its view, so only the fixture state below drives what these tests observe.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        }
        composeRule.setContent { AstroTheme { AppShellRoute(shellState = shellState) } }
    }

    private companion object {
        /** Shaped like the contract's example screen: calendar, then expense. */
        val FIXTURE_TABS =
            listOf(
                AppShellTab(
                    destinationId = "calendar",
                    label = "ปฏิทิน",
                    iconToken = "icon.calendar",
                    targetScreenId = "calendar",
                ),
                AppShellTab(
                    destinationId = "expense",
                    label = "ค่าใช้จ่าย",
                    iconToken = "icon.wallet",
                    targetScreenId = "expense",
                ),
            )
    }
}

private val inBottomBar: SemanticsMatcher = hasAnyAncestor(hasTestTag(AppShellTestTags.BOTTOM_BAR))

/** The bottom-bar item labelled [label] — not the placeholder that shows the same text. */
private fun ComposeContentTestRule.tabItem(label: String) =
    onNode(inBottomBar and isSelectable() and hasText(label))

private fun ComposeContentTestRule.assertTabLabelsInOrder(labels: List<String>) {
    val items = onAllNodes(inBottomBar and isSelectable())
    items.assertCountEquals(labels.size)
    labels.forEachIndexed { index, label -> items[index].assertTextEquals(label) }
}

private fun ComposeContentTestRule.assertOnlyPlaceholderShownIs(
    shown: AppShellTab,
    tabs: List<AppShellTab>,
) {
    // The label as text inside the shown destination's placeholder, not the bar item's label.
    onNode(
            hasAnyAncestor(
                hasTestTag(AppShellTestTags.destinationPlaceholder(shown.destinationId))
            ) and hasText(shown.label)
        )
        .assertIsDisplayed()
    tabs
        .filter { it.destinationId != shown.destinationId }
        .forEach {
            onNodeWithTag(AppShellTestTags.destinationPlaceholder(it.destinationId))
                .assertDoesNotExist()
        }
}
