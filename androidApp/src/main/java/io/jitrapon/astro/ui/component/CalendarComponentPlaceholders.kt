package io.jitrapon.astro.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.jitrapon.astro.R
import io.jitrapon.astro.presentation.calendar.AgendaBodyUiState
import io.jitrapon.astro.presentation.calendar.EventChipUiState
import io.jitrapon.astro.presentation.calendar.MonthBodyUiState

// Stand-ins for the real calendar renderers until the month grid and agenda list are built. Each is
// tagged with the id of the component it draws, so a test can tell which renderer the registry
// resolved.

@Composable
internal fun MonthBodyPlaceholder(body: MonthBodyUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(CalendarComponentTestTags.registered(body.componentId))) {
        Text(text = body.headerLabel, style = MaterialTheme.typography.h6)
    }
}

@Composable
internal fun AgendaBodyPlaceholder(body: AgendaBodyUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(CalendarComponentTestTags.registered(body.componentId))) {
        body.days.forEach { day ->
            Text(text = day.headerLabel, style = MaterialTheme.typography.subtitle1)
        }
    }
}

@Composable
internal fun MonthAllDayBarPlaceholder(event: EventChipUiState, modifier: Modifier = Modifier) {
    EventTitlePlaceholder(event, modifier)
}

@Composable
internal fun MonthTimedMarkerPlaceholder(event: EventChipUiState, modifier: Modifier = Modifier) {
    EventTitlePlaceholder(event, modifier)
}

@Composable
internal fun TimeGridAllDayBarPlaceholder(event: EventChipUiState, modifier: Modifier = Modifier) {
    EventTitlePlaceholder(event, modifier)
}

@Composable
internal fun EventBlockPlaceholder(event: EventChipUiState, modifier: Modifier = Modifier) {
    EventTitlePlaceholder(event, modifier)
}

@Composable
internal fun EventCardPlaceholder(event: EventChipUiState, modifier: Modifier = Modifier) {
    EventTitlePlaceholder(event, modifier)
}

@Composable
private fun EventTitlePlaceholder(event: EventChipUiState, modifier: Modifier = Modifier) {
    Text(
        text = event.title,
        modifier = modifier.testTag(CalendarComponentTestTags.registered(event.componentId)),
    )
}

/**
 * What the registry draws for a component id it holds no renderer for: the id itself, so an
 * unregistered component is visible and nameable rather than a silent gap in the screen.
 */
@Composable
internal fun UnregisteredComponent(componentId: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.calendar_component_unregistered, componentId),
        modifier = modifier.testTag(CalendarComponentTestTags.unregistered(componentId)),
        style = MaterialTheme.typography.caption,
    )
}
