package io.jitrapon.astro.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.ChipStyle
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.presentation.calendar.EventChipUiState

// Stand-ins for the real event-presentation renderers. Each reads only its own chip's props — the
// subtitle lines already capped and the chip style already scoped by the shared projection — and is
// tagged with the id of the component it draws.

/** A month all-day bar: a filled bar, drawn per the resolved chip style. */
@Composable
internal fun MonthAllDayBarPlaceholder(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledBarChip(event, onAction, modifier)
}

/** A month timed marker: a dot in the calendar's accent before its one server-composed line. */
@Composable
internal fun MonthTimedMarkerPlaceholder(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = event.calendarColor.toChipColors()
    Row(
        modifier = modifier.eventChipRoot(event, onAction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
        ChipText(event, color = MaterialTheme.colors.onSurface)
    }
}

/** A time-grid all-day bar: a filled bar, drawn per the resolved chip style. */
@Composable
internal fun TimeGridAllDayBarPlaceholder(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledBarChip(event, onAction, modifier)
}

/** A time-grid block. Chip style does not reach it, so it keeps one look under every style. */
@Composable
internal fun EventBlockPlaceholder(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    AccentEdgeChip(event, onAction, modifier)
}

/** An agenda card. Chip style does not reach it, so it keeps one look under every style. */
@Composable
internal fun EventCardPlaceholder(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    AccentEdgeChip(event, onAction, modifier)
}

/**
 * The filled-bar components chip style reaches. The shared projection leaves [EventChipUiState]'s
 * style `null` only on components it does not reach, so a `null` here is not expected; it draws the
 * accent-edge treatment rather than failing.
 */
@Composable
private fun FilledBarChip(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (event.chipStyle) {
        ChipStyle.PASTEL -> PastelChip(event, onAction, modifier)
        ChipStyle.ACCENT_EDGE,
        null -> AccentEdgeChip(event, onAction, modifier)
    }
}

/** A pastel fill in the calendar's background colour, with its foreground colour on top. */
@Composable
private fun PastelChip(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = event.calendarColor.toChipColors()
    Box(
        modifier =
            modifier
                .eventChipRoot(event, onAction)
                .clip(CHIP_SHAPE)
                .background(colors.background)
                .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        ChipText(event, color = colors.foreground)
    }
}

/** A surface-coloured chip marked by an edge in the calendar's accent colour. */
@Composable
private fun AccentEdgeChip(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = event.calendarColor.toChipColors()
    Row(
        modifier =
            modifier
                .eventChipRoot(event, onAction)
                .height(IntrinsicSize.Min)
                .clip(CHIP_SHAPE)
                .background(MaterialTheme.colors.surface)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(colors.accent))
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            ChipText(event, color = MaterialTheme.colors.onSurface)
        }
    }
}

/**
 * The chip's title and every subtitle line it carries. The lines arrive already capped at the
 * resolved chip density, so this draws them all and derives no cap of its own.
 */
@Composable
private fun ChipText(event: EventChipUiState, color: Color) {
    Column {
        Text(
            text = event.title,
            color = color,
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        event.subtitleLines.forEach { line ->
            Text(
                text = line.text,
                modifier = Modifier.testTag(CalendarComponentTestTags.subtitleLine(event.eventId)),
                color = color,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The root of every event chip: tagged with the component id the registry resolved it by, tappable
 * to open the event's detail, and speaking the event's accessibility label, when it has one, in
 * place of its drawn text.
 *
 * The contract attaches no action to an event, so the tap builds its own from the event's id —
 * which is what makes the open-event-detail action reachable at all.
 */
private fun Modifier.eventChipRoot(event: EventChipUiState, onAction: (Action) -> Unit): Modifier {
    val tappable =
        testTag(CalendarComponentTestTags.registered(event.componentId)).clickable {
            onAction(OpenEventDetailAction(event.eventId))
        }
    val label = event.accessibilityLabel ?: return tappable
    return tappable.semantics(mergeDescendants = true) { contentDescription = label }
}

private val CHIP_SHAPE = RoundedCornerShape(4.dp)
