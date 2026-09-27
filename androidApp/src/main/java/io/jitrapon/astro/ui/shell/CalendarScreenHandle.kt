package io.jitrapon.astro.ui.shell

import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.calendar.CalendarUiState
import io.jitrapon.astro.presentation.calendar.CalendarViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shell needs from the calendar screen it is drawn around: the screen's state, and the way
 * to act on it.
 *
 * The shell holds this rather than a [CalendarViewModel], so a test can stand a scripted screen in
 * for the real one and still drive every selection through the shell's own dispatch path — the real
 * view model is reachable only over a backend a minified test build cannot talk to.
 */
class CalendarScreenHandle(
    /** The screen's state, as [CalendarViewModel.state] publishes it. */
    val state: StateFlow<CalendarUiState>,
    /**
     * Acts on the screen. Returns the effect the platform must carry out, or `null` when the screen
     * consumed the action itself — as a view switch is.
     */
    val dispatch: (Action) -> ActionEffect?,
)

/** This view model as the handle the shell drives it through. */
fun CalendarViewModel.toCalendarScreenHandle(): CalendarScreenHandle =
    CalendarScreenHandle(state = state, dispatch = ::dispatch)
