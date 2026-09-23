package io.jitrapon.astro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import io.jitrapon.astro.ui.main.MainActivity
import io.jitrapon.astro.ui.shell.AppShellTestTags
import org.junit.Rule
import org.junit.Test

/**
 * Launches the app exactly as a user would and waits for the shell to settle — the check that R8
 * left the running app whole, which no unshrunk build can make.
 *
 * Black-box by design. Instrumented tests run against a minified build whose keep rules are
 * generated from what the tests reference, so every app type a test touches is kept in the APK
 * under test. Reaching into the dependency graph or the repository from here would keep precisely
 * the classes this test exists to catch R8 stripping. It references only [MainActivity] — kept by
 * the manifest regardless — and tag strings the compiler inlines.
 */
class MinifiedAppSmokeTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The failure placeholder is the one settled state this build can reach deterministically:
     * cleartext to the development backend is permitted only by the debug build's network-security
     * overlay, so the first fetch here always fails. Reaching it proves the whole release path ran
     * under R8 — the Application started the graph, the view model's factory resolved the
     * repository from it, the query layer ran the exchange, the client's failure became an error
     * result, and the shared projection drew the shell. A stripped class anywhere on that path
     * crashes the activity or leaves the shell loading, and the wait below fails.
     */
    @Test
    fun launchResolvesTheGraphAndSettlesTheShellOnTheFetchFailure() {
        composeRule.waitUntil(timeoutMillis = SHELL_SETTLE_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithTag(AppShellTestTags.FAILURE)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(AppShellTestTags.FAILURE).assertIsDisplayed()
    }

    private companion object {
        // Cleartext is refused before any socket opens, so the failure arrives well inside this;
        // the margin is for a cold emulator's first composition.
        const val SHELL_SETTLE_TIMEOUT_MILLIS = 15_000L
    }
}
