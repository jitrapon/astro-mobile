package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What a collector sees between subscribing and the first observed state reaching it.
 *
 * Only ever visible in that gap. The observation publishes its current state the moment it is
 * subscribed to, so this stands for the instant before that arrives rather than for a state the
 * layer ever decides to be in — which is why it claims neither content nor activity.
 */
private val NOTHING_SHOWING_YET = CalendarUiState(content = null, isLoading = false, failure = null)

/**
 * Holds one calendar screen's state for as long as something is looking at it: it observes the
 * screen matching [request] and publishes what a renderer paints.
 *
 * **It owns no scope.** [scope] is supplied by whoever created it and is the only thing that ends
 * the collection — an Android `ViewModel`'s `viewModelScope`, or a scope tied to whatever presents
 * the screen. A view model that built its own would have no answer to when it should be cancelled,
 * and the exchange it is waiting on already outlives it by design: the data layer hosts round trips
 * on its own scope, so a screen going away abandons a wait and cancels nothing.
 *
 * **The graph does not build one.** A view model is per screen and per request, so it is
 * constructed where a screen is, with the repository resolved from the graph and the scope the
 * presenter's.
 *
 * The whole class is hidden from the generated Objective-C header. It is `public` because
 * `:androidApp` is a separate module and `internal` would not reach it, but its state is a
 * [StateFlow] and its constructor takes a [CoroutineScope] — either of which would put a coroutines
 * type on the iOS framework's public surface, the one thing that surface is guarded against. Swift
 * does not lose the seam: it collects the observation through the iOS adapter, which turns a
 * subscription into something Objective-C can drive.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class CalendarViewModel(
    calendarScreenRepository: CalendarScreenRepository,
    request: CalendarScreenRequest,
    scope: CoroutineScope,
) {

    /**
     * The screen as it stands now, and every state that replaces it until [scope] ends.
     *
     * Nothing is observed until something collects this, and the observation stops when the last
     * collector leaves — collecting is what asks the data layer for a screen, so an unwatched
     * screen costs neither an exchange nor a subscription. A collector that returns is served the
     * last state immediately and re-observes, which is how the screen is brought up to date; that
     * cost is bounded by the remembered screen's staleness window rather than by a timeout here,
     * since a re-observation inside that window is answered from memory and exchanges nothing.
     *
     * Once [scope] is cancelled this stops changing. It does not complete and it reports no
     * failure, because a screen that went away was not a failure to load one.
     */
    val state: StateFlow<CalendarUiState> =
        calendarScreenRepository
            .observeCalendarScreen(request)
            .map { it.toCalendarUiState() }
            .stateIn(scope, SharingStarted.WhileSubscribed(), NOTHING_SHOWING_YET)
}
