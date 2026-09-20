package io.jitrapon.astro.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.jitrapon.astro.R
import io.jitrapon.astro.presentation.shell.AppShellTab

/** What stands in for a destination's screen until that screen is built. */
@Composable
internal fun DestinationPlaceholder(tab: AppShellTab) {
    CenteredMessage(
        text = tab.label,
        modifier = Modifier.testTag(AppShellTestTags.destinationPlaceholder(tab.destinationId)),
    )
}

@Composable
internal fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    ShellBackdrop(modifier) {
        CenteredMessage(
            text = stringResource(R.string.app_shell_loading),
            modifier = Modifier.testTag(AppShellTestTags.LOADING),
            leading = { CircularProgressIndicator() },
        )
    }
}

/**
 * What the shell shows once a screen has arrived carrying nowhere to go. No progress indicator:
 * unlike [LoadingPlaceholder] this state is settled, and nothing further is coming to replace it.
 */
@Composable
internal fun NoDestinationsPlaceholder(modifier: Modifier = Modifier) {
    ShellBackdrop(modifier) {
        CenteredMessage(
            text = stringResource(R.string.app_shell_no_destinations),
            modifier = Modifier.testTag(AppShellTestTags.NO_DESTINATIONS),
        )
    }
}

@Composable
internal fun FailurePlaceholder(modifier: Modifier = Modifier) {
    ShellBackdrop(modifier) {
        CenteredMessage(
            text = stringResource(R.string.app_shell_failure),
            modifier = Modifier.testTag(AppShellTestTags.FAILURE),
        )
    }
}

/**
 * The full-screen backdrop the shell's barless states draw on.
 *
 * They render outside the shell's scaffold, which is what supplies a screen its window background
 * *and* the content colour that belongs with it. Without one, a message keeps Material's default
 * black content colour and disappears against a dark window background. Insets are taken here for
 * the same reason: on these branches no scaffold is applying them.
 */
@Composable
private fun ShellBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
        Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
    }
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
