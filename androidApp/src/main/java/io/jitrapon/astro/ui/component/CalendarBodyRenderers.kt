package io.jitrapon.astro.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.jitrapon.astro.R
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.data.calendar.WeekStart
import io.jitrapon.astro.presentation.calendar.AgendaBodyUiState
import io.jitrapon.astro.presentation.calendar.MonthBodyUiState
import java.text.DateFormatSymbols
import java.util.Calendar

// Stand-ins for the real calendar body renderers until the month grid and agenda list are built.
// Each reads only its own component's props and is tagged with the id of the component it draws,
// so a test can tell which renderer the registry resolved.

/**
 * The month body: its heading, a weekday row starting on the resolved week start, and its events in
 * the server's display order. No grid yet — the rows the week start orders are laid out when the
 * month grid is built.
 *
 * Past [MAX_VISIBLE_MONTH_EVENTS] events the rest collapse into one "more" affordance, which
 * presents the hidden events. The contract attaches no action to an overflow either, so the
 * affordance builds its own from the ids it hides — making the present-modal action reachable.
 */
@Composable
internal fun MonthBodyPlaceholder(
    body: MonthBodyUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = body.events.take(MAX_VISIBLE_MONTH_EVENTS)
    val hidden = body.events.drop(MAX_VISIBLE_MONTH_EVENTS)
    Column(
        modifier = modifier.testTag(CalendarComponentTestTags.registered(body.componentId)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        body.headerLabel?.let { Text(text = it, style = MaterialTheme.typography.h6) }
        WeekdayRow(body.weekStart)
        visible.forEach { event ->
            CalendarEventComponent(event, onAction, Modifier.fillMaxWidth())
        }
        if (hidden.isNotEmpty()) {
            Text(
                text = stringResource(R.string.calendar_month_more_events, hidden.size),
                modifier =
                    Modifier.testTag(CalendarComponentTestTags.MONTH_OVERFLOW).clickable {
                        onAction(PresentModalAction(hidden.map { it.eventId }))
                    },
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary,
            )
        }
    }
}

/** The agenda body: each day's heading followed by that day's events, in delivered order. */
@Composable
internal fun AgendaBodyPlaceholder(
    body: AgendaBodyUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(CalendarComponentTestTags.registered(body.componentId)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        body.days.forEach { day ->
            Text(text = day.headerLabel, style = MaterialTheme.typography.subtitle1)
            day.events.forEach { event ->
                CalendarEventComponent(event, onAction, Modifier.fillMaxWidth())
            }
        }
    }
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

/** Seven weekday labels, starting on [weekStart] — the order the month grid's rows will take. */
@Composable
private fun WeekdayRow(weekStart: WeekStart) {
    val labels = remember(weekStart) { weekdayLabelsStartingOn(weekStart) }
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { position, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f).testTag(CalendarComponentTestTags.weekday(position)),
                style = MaterialTheme.typography.caption,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The device locale's short weekday names in grid order. [DateFormatSymbols] indexes them by
 * [Calendar]'s day constants, `SUNDAY` (1) through `SATURDAY` (7); it is used rather than
 * `java.time`, which this app's minimum SDK predates.
 */
private fun weekdayLabelsStartingOn(weekStart: WeekStart): List<String> {
    val names = DateFormatSymbols.getInstance().shortWeekdays
    val first =
        when (weekStart) {
            WeekStart.MONDAY -> Calendar.MONDAY
            WeekStart.SUNDAY -> Calendar.SUNDAY
        }
    return List(DAYS_IN_WEEK) { offset -> names[(first - 1 + offset) % DAYS_IN_WEEK + 1] }
}

private const val DAYS_IN_WEEK = 7

/** How many events the month placeholder lists before collapsing the rest into "more". */
private const val MAX_VISIBLE_MONTH_EVENTS = 3
