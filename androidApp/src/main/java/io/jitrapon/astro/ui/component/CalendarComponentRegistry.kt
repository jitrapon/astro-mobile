package io.jitrapon.astro.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.CalendarComponentIds
import io.jitrapon.astro.presentation.calendar.AgendaBodyUiState
import io.jitrapon.astro.presentation.calendar.CalendarBodyUiState
import io.jitrapon.astro.presentation.calendar.EventChipUiState
import io.jitrapon.astro.presentation.calendar.MonthBodyUiState

/**
 * Draws one calendar body the server selected, reporting each action the person takes on it to the
 * `(Action) -> Unit` it is handed.
 */
internal typealias CalendarBodyRenderer =
    @Composable (CalendarBodyUiState, (Action) -> Unit, Modifier) -> Unit

/** Draws one event in the presentation component the server selected for it, reporting its tap. */
internal typealias CalendarEventRenderer =
    @Composable (EventChipUiState, (Action) -> Unit, Modifier) -> Unit

/**
 * Maps each server-driven calendar component, by the versioned id the contract delivers it as, to
 * the composable that draws it on Android.
 *
 * The ids are [CalendarComponentIds]' constants — the same ones `:shared` decodes with — so this
 * registry cannot key on a spelling the decoder never produces. An id with no renderer resolves to
 * [UnregisteredComponent], which draws the id it could not render rather than nothing.
 *
 * Neither fallback is reachable from a delivered response today. The decoder has no default for an
 * unknown `component`, so a body or event presentation it does not model — including the contract's
 * `calendar.timeGrid.v1` and `calendar.year.v1` bodies — fails the whole response rather than
 * reaching a registry, and every id it does model is registered here. What does reach the fallback
 * is a component `:shared` models before this registry is taught it, or a hand-built state naming
 * an id nothing draws; the registry test enumerates [CalendarComponentIds.BODY_IDS] and
 * [CalendarComponentIds.EVENT_PRESENTATION_IDS] so the first case fails a test instead of shipping
 * a fallback.
 */
internal object CalendarComponentRegistry {

    private val bodyRenderers: Map<String, CalendarBodyRenderer> =
        mapOf(
            CalendarComponentIds.MONTH_BODY to
                bodyRenderer<MonthBodyUiState> { body, onAction, modifier ->
                    MonthBodyPlaceholder(body, onAction, modifier)
                },
            CalendarComponentIds.AGENDA_BODY to
                bodyRenderer<AgendaBodyUiState> { body, onAction, modifier ->
                    AgendaBodyPlaceholder(body, onAction, modifier)
                },
        )

    private val eventRenderers: Map<String, CalendarEventRenderer> =
        mapOf(
            CalendarComponentIds.MONTH_ALL_DAY_BAR to
                eventRenderer { event, onAction, modifier ->
                    MonthAllDayBarPlaceholder(event, onAction, modifier)
                },
            CalendarComponentIds.MONTH_TIMED_MARKER to
                eventRenderer { event, onAction, modifier ->
                    MonthTimedMarkerPlaceholder(event, onAction, modifier)
                },
            CalendarComponentIds.TIME_GRID_ALL_DAY_BAR to
                eventRenderer { event, onAction, modifier ->
                    TimeGridAllDayBarPlaceholder(event, onAction, modifier)
                },
            CalendarComponentIds.EVENT_BLOCK to
                eventRenderer { event, onAction, modifier ->
                    EventBlockPlaceholder(event, onAction, modifier)
                },
            CalendarComponentIds.EVENT_CARD to
                eventRenderer { event, onAction, modifier ->
                    EventCardPlaceholder(event, onAction, modifier)
                },
        )

    /** The renderer registered for the body component [componentId], or the fallback. */
    fun bodyRendererFor(componentId: String): CalendarBodyRenderer =
        bodyRenderers[componentId] ?: UNREGISTERED_BODY

    /**
     * The renderer registered for the event-presentation component [componentId], or the fallback.
     */
    fun eventRendererFor(componentId: String): CalendarEventRenderer =
        eventRenderers[componentId] ?: UNREGISTERED_EVENT

    private val UNREGISTERED_BODY: CalendarBodyRenderer = { body, _, modifier ->
        UnregisteredComponent(body.componentId, modifier)
    }

    private val UNREGISTERED_EVENT: CalendarEventRenderer = { event, _, modifier ->
        UnregisteredComponent(event.componentId, modifier)
    }

    /** Gives a lambda literal the renderer type, which a bare lambda beside `to` cannot infer. */
    private fun eventRenderer(render: CalendarEventRenderer): CalendarEventRenderer = render

    /**
     * A body renderer that draws only the [T] its component id is paired with. The shared
     * projection builds each body state with its own branch's id, so a mismatch can only come from
     * a hand-built state — and it falls back visibly rather than throwing a cast failure mid-frame.
     */
    private inline fun <reified T : CalendarBodyUiState> bodyRenderer(
        noinline render: @Composable (T, (Action) -> Unit, Modifier) -> Unit
    ): CalendarBodyRenderer = { body, onAction, modifier ->
        if (body is T) {
            render(body, onAction, modifier)
        } else {
            UnregisteredComponent(body.componentId, modifier)
        }
    }
}

/**
 * Draws [body] with the renderer its component id is registered to, reporting each action taken on
 * it to [onAction].
 */
@Composable
internal fun CalendarBodyComponent(
    body: CalendarBodyUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    CalendarComponentRegistry.bodyRendererFor(body.componentId)(body, onAction, modifier)
}

/**
 * Draws [event] with the renderer its presentation component id is registered to, reporting its tap
 * to [onAction].
 */
@Composable
internal fun CalendarEventComponent(
    event: EventChipUiState,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    CalendarComponentRegistry.eventRendererFor(event.componentId)(event, onAction, modifier)
}
