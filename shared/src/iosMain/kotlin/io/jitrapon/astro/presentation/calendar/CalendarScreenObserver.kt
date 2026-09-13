package io.jitrapon.astro.presentation.calendar

import io.jitrapon.astro.data.calendar.CalendarScreenRepository
import io.jitrapon.astro.data.calendar.CalendarScreenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * How Swift watches a calendar screen: it hands over a request and a callback, receives every
 * [CalendarUiState] the screen passes through, and keeps the returned [CalendarScreenSubscription]
 * for as long as it wants them.
 *
 * **Why a callback and a handle.** A Kotlin `Flow` does not cross into Swift — Objective-C interop
 * bridges `suspend` functions to `async`, and nothing bridges a stream — so the subscription is
 * turned into the two things Objective-C can drive: a closure that is called, and an object that is
 * cancelled. Swift wraps that pair in whatever its screen needs (an `AsyncStream`, an observable
 * model) without Kotlin choosing for it.
 *
 * **Why hand-rolled rather than SKIE.** SKIE regenerates the framework's Swift surface so a `Flow`
 * arrives as an `AsyncSequence` and a sealed interface as an exhaustively switchable enum. It was
 * rejected for now: it is a compiler plugin locked to the Kotlin version, so every Kotlin bump
 * waits on it; once a third-party plugin authors the Swift surface, checking the generated header
 * alone no longer proves what Swift can bind to; and with a single flat state crossing here, the
 * exhaustiveness it buys has nothing to act on. That changes once sealed types are rendered from
 * Swift — which is when it is worth re-opening, and why this class stays thin enough to replace in
 * one file.
 *
 * **Why the view model's state rather than the observation's.** Each subscription builds its own
 * [CalendarViewModel] and forwards what it publishes, so the projection from what the data layer
 * observed to what a renderer paints is stated once, in Kotlin, and shared with Android — rather
 * than restated as a downcast over the observation's cases by every SwiftUI screen, with nothing to
 * say when a new case goes unhandled.
 *
 * **Delivery is on [deliveryScope]'s dispatcher and never inside [observe].** In the app that is
 * the main thread, so a callback may touch UI state directly. It is also never synchronous: the
 * first state is dispatched, not delivered in the caller's frame, so a Swift caller always holds
 * the handle before anything reaches its callback and cannot be re-entered mid-assignment.
 *
 * The constructor is internal — the graph builds the one instance, and a [CoroutineScope] parameter
 * would otherwise put a coroutines type on the framework surface.
 */
class CalendarScreenObserver
internal constructor(
    private val calendarScreenRepository: CalendarScreenRepository,
    private val deliveryScope: CoroutineScope,
) {

    /**
     * Starts watching the screen matching [request], calling [onState] with the state as it stands
     * and with every state that replaces it, until the returned subscription is cancelled.
     *
     * Nothing is ever delivered that reports a cancellation: a subscription that is cancelled
     * simply stops hearing about the screen, even while an exchange for it is still in flight.
     */
    fun observe(
        request: CalendarScreenRequest,
        onState: (CalendarUiState) -> Unit,
    ): CalendarScreenSubscription {
        // One child coroutine of the delivery scope per subscription, and the view model's scope is
        // that coroutine's own: the sharing coroutine the view model starts is its child, so
        // cancelling the subscription ends both, cancelling one subscription ends no other, and
        // tearing down the graph still ends every subscription at once.
        val subscription = deliveryScope.launch {
            coroutineScope {
                CalendarViewModel(
                        calendarScreenRepository = calendarScreenRepository,
                        request = request,
                        scope = this,
                    )
                    .state
                    .collect { state -> onState(state) }
            }
        }
        return CalendarScreenSubscription(subscription)
    }
}

/**
 * The handle Swift holds for one [CalendarScreenObserver.observe] call; cancelling it is the only
 * thing that ends the subscription short of the whole graph being torn down.
 */
class CalendarScreenSubscription internal constructor(private val subscription: Job) {

    /**
     * Stops delivery and releases the subscription's coroutines.
     *
     * Safe to call more than once and from any thread. Called on the delivery thread, it also holds
     * back a state that was already queued for delivery but had not yet reached the callback, so
     * nothing arrives after this returns. The exchange the screen was waiting on is not cancelled
     * with it — the data layer runs round trips on its own scope, so another observer of the same
     * request still receives what it brings back.
     */
    fun cancel() {
        subscription.cancel()
    }
}
