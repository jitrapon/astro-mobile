package io.jitrapon.astro.ui.shell

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.jitrapon.astro.R
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.presentation.calendar.CalendarBodyUiState
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.presentation.calendar.ViewSwitcherOptionUiState
import io.jitrapon.astro.presentation.calendar.ViewSwitcherUiState
import io.jitrapon.astro.ui.component.CalendarBodyComponent

/**
 * The calendar screen's top bar: the server-formatted [title], below it one option per view the
 * screen offers, the active one selected, and under those the state of the screen's request.
 *
 * The shell keeps the painted screen — and so this bar — while a switch loads or after it fails, so
 * the request's state has to show here or nowhere: [requestStatus] draws a progress line while the
 * request is in flight, and says the view did not load — and that choosing it again retries it —
 * once it has failed.
 *
 * An option is selectable even while it is active — choosing it again dispatches an equal request,
 * which the view model treats as no change — so the row needs no state of its own. The bar takes
 * the status bar's insets itself; Material's default [TopAppBar] overload applies none, which on an
 * edge-to-edge window would draw the title under the status bar.
 */
@Composable
internal fun CalendarTopBar(
    title: String,
    viewSwitcher: ViewSwitcherUiState?,
    requestStatus: CalendarRequestStatus,
    onViewSelected: (ViewSwitcherOptionUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(AppShellTestTags.CALENDAR_TOP_BAR),
        color = MaterialTheme.colors.primarySurface,
        elevation = AppBarDefaults.TopAppBarElevation,
    ) {
        Column {
            TopAppBar(
                title = { Text(title) },
                windowInsets = AppBarDefaults.topAppBarWindowInsets,
                elevation = 0.dp,
            )
            if (viewSwitcher != null) {
                ViewSwitcherRow(viewSwitcher, onViewSelected)
            }
            when (requestStatus) {
                CalendarRequestStatus.LOADING ->
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth().testTag(AppShellTestTags.CALENDAR_LOADING)
                    )
                CalendarRequestStatus.FAILED ->
                    Text(
                        text = stringResource(R.string.calendar_screen_failure),
                        modifier =
                            Modifier.testTag(AppShellTestTags.CALENDAR_FAILURE)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.caption,
                    )
                CalendarRequestStatus.SETTLED -> Unit
            }
        }
    }
}

/** Where the calendar screen's own request stands, as its top bar shows it. */
internal enum class CalendarRequestStatus {
    SETTLED,
    LOADING,
    FAILED,
}

/** This state's request status; one still in flight shows as loading even beside a failure. */
internal fun CalendarUiState.requestStatus(): CalendarRequestStatus =
    when {
        isLoading -> CalendarRequestStatus.LOADING
        failure != null -> CalendarRequestStatus.FAILED
        else -> CalendarRequestStatus.SETTLED
    }

/** The switcher's options in contract order, scrolling sideways when they overflow the bar. */
@Composable
private fun ViewSwitcherRow(
    viewSwitcher: ViewSwitcherUiState,
    onViewSelected: (ViewSwitcherOptionUiState) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        viewSwitcher.options.forEach { option ->
            Text(
                text = option.label,
                modifier =
                    Modifier.testTag(AppShellTestTags.viewSwitcherOption(option.id))
                        .selectable(
                            selected = option.isActive,
                            role = Role.Tab,
                            onClick = { onViewSelected(option) },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                fontWeight = if (option.isActive) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * The calendar screen's body, drawn by whichever renderer its component is registered to, with what
 * the person does on it reported to [onAction].
 */
@Composable
internal fun CalendarScreenBody(
    body: CalendarBodyUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        CalendarBodyComponent(body, onAction)
    }
}
