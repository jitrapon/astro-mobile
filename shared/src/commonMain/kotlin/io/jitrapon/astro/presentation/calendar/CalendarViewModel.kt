package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.Action
import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import io.jitrapon.astro.data.calendar.CalendarViewSelection
import io.jitrapon.astro.data.calendar.NavigateAction
import io.jitrapon.astro.data.calendar.OpenEventDetailAction
import io.jitrapon.astro.data.calendar.OpenUrlAction
import io.jitrapon.astro.data.calendar.PresentModalAction
import io.jitrapon.astro.data.calendar.SwitchCalendarViewAction
import io.jitrapon.astro.data.calendar.toRequestedCalendarView
import io.jitrapon.astro.presentation.action.ActionEffect
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
 * screen matching the current request — [request] until an action re-points it — and publishes what
 * a renderer paints.
 *
 * **It owns no scope.** [scope] is supplied by whoever created it and is the only thing that ends
 * the collection — an Android `ViewModel`'s `viewModelScope`, or a scope tied to whatever presents
 * the screen. A view model that built its own would have no answer to when it should be cancelled,
 * and the exchange it is waiting on already outlives it by design: the data layer hosts round trips
 * on its own scope, so a screen going away abandons a wait and cancels nothing.
 *
 * **The graph does not build one.** A view model is per screen, so it is constructed where a screen
 * is, with the repository resolved from the graph and the scope the presenter's.
 *
 * The whole class is hidden from the generated Objective-C header. It is `public` because
 * `:androidApp` is a separate module and `internal` would not reach it, but its state is a
 * [StateFlow] and its constructor takes a [CoroutineScope] — either of which would put a coroutines
 * type on the iOS framework's public surface, the one thing that surface is guarded against. Swift
 * does not lose the seam: it collects the observation through the iOS adapter, which turns a
 * subscription into something Objective-C can drive.
 */
@OptIn(ExperimentalObjCRefinement::class, ExperimentalCoroutinesApi::class)
@HiddenFromObjC
class CalendarViewModel(
    calendarScreenRepository: CalendarScreenRepository,
    request: CalendarScreenRequest,
    scope: CoroutineScope,
) {

    /**
     * The request whose screen is observed. Only [dispatch] moves it, and only its view — the
     * window, zone, locale and known theme a presenter chose stay as they were.
     */
    private val observedRequest = MutableStateFlow(request)

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
     * When the request is re-pointed, the previous request's observation is cancelled — never
     * reported as a failure — and its screen stays the content until the new request delivers one
     * of its own; see [PaintedState.followedBy].
     *
     * Once [scope] is cancelled this stops changing. It does not complete and it reports no
     * failure, because a screen that went away was not a failure to load one.
     */
    val state: StateFlow<CalendarUiState> =
        observedRequest
            .flatMapLatest { observed ->
                calendarScreenRepository.observeCalendarScreen(observed).map {
                    ObservedRequestState(observed, it.toCalendarUiState())
                }
            }
            .runningFold(null) { painted: PaintedState?, next -> painted.followedBy(next) }
            .filterNotNull()
            .map { it.uiState }
            .stateIn(scope, SharingStarted.WhileSubscribed(), NOTHING_SHOWING_YET)

    /**
     * Carries out [action] as far as the shared layer can, and returns what the platform must do
     * for the rest — or `null` when nothing is left for it to do.
     *
     * Only a [SwitchCalendarViewAction] is consumed here: it re-points the observed request, and
     * the platform learns of the switch through [state]. Every other action needs a platform
     * surface — navigation, a browser, a modal — so it comes back as the [ActionEffect] naming it.
     */
    fun dispatch(action: Action): ActionEffect? =
        when (action) {
            is SwitchCalendarViewAction -> {
                switchView(action.selection)
                null
            }
            is NavigateAction -> ActionEffect.ShowScreen(action.screen)
            is OpenUrlAction -> ActionEffect.OpenExternalUrl(action.url)
            is OpenEventDetailAction -> ActionEffect.ShowEventDetail(action.eventId)
            is PresentModalAction -> ActionEffect.ShowEvents(action.eventIds)
        }

    /**
     * Re-points the observed request at [selection]'s view. A selection no request can express
     * leaves the view where it is; one naming the view already observed changes nothing, since an
     * equal request is not a new one.
     */
    private fun switchView(selection: CalendarViewSelection) {
        val view = selection.toRequestedCalendarView() ?: return
        observedRequest.update { it.copy(view = view) }
    }
}

/** One observed state, tagged with the request it was observed for. */
private class ObservedRequestState(
    val request: CalendarScreenRequest,
    val uiState: CalendarUiState,
)

/**
 * What was last painted, for which request, and whether that request has delivered a screen of its
 * own since it became the one observed.
 */
private class PaintedState(
    val request: CalendarScreenRequest,
    val uiState: CalendarUiState,
    val requestHasOwnContent: Boolean,
)

/**
 * What to paint once [next] arrives after this.
 *
 * A request that has not yet delivered a screen of its own keeps painting the screen that was
 * showing before it — so a view switch shows the previous view, with the new request's loading flag
 * and any failure, until the new view arrives. Without it every switch would blank the screen, and
 * with it the tabs and the view switcher that live inside a screen, stranding a user whose switch
 * failed on a screen with nothing left to switch back with.
 *
 * Once the request has delivered its own screen, its states are painted as observed, including one
 * with no content: that is an invalidation disowning the request's screen, and the screen that
 * preceded the request is no truer an answer than none.
 */
private fun PaintedState?.followedBy(next: ObservedRequestState): PaintedState {
    val sameRequest = this != null && request == next.request
    val requestHasOwnContent =
        next.uiState.content != null || (sameRequest && this.requestHasOwnContent)
    val uiState =
        if (requestHasOwnContent) next.uiState
        else next.uiState.copy(content = this?.uiState?.content)
    return PaintedState(next.request, uiState, requestHasOwnContent)
}
