package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.ViewSwitcher

/**
 * The delivered calendar view chooser, reduced to what a platform draws: the options in contract
 * order, at most one of them marked active.
 */
data class ViewSwitcherUiState(val options: List<ViewSwitcherOptionUiState>)

/** One offered calendar view. */
data class ViewSwitcherOptionUiState(
    val id: String,
    /** Server-formatted label for the view. */
    val label: String,
    /** Whether this is the view the screen is currently showing. */
    val isActive: Boolean,
    /**
     * The action choosing this option dispatches — built from the option's own selection, since the
     * contract attaches no action to a switcher option.
     */
    val action: SwitchCalendarViewAction,
)

/**
 * This switcher with each option paired with the action selecting it, in contract order.
 *
 * The active option is the **first** whose selection equals `activeSelection`. The contract does
 * not forbid two options carrying the same selection, and marking both would draw two highlighted
 * options for one active view; nor does it forbid an `activeSelection` no option offers, which
 * marks nothing active rather than failing the screen.
 */
internal fun ViewSwitcher.toViewSwitcherUiState(): ViewSwitcherUiState {
    val activeIndex = options.indexOfFirst { it.selection == activeSelection }
    return ViewSwitcherUiState(
        options =
            options.mapIndexed { index, option ->
                ViewSwitcherOptionUiState(
                    id = option.id,
                    label = option.label,
                    isActive = index == activeIndex,
                    action = SwitchCalendarViewAction(option.selection),
                )
            }
    )
}
