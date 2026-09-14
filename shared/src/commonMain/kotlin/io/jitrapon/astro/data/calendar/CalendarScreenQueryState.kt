package io.jitrapon.astro.data.calendar

/**
 * What an observation of one calendar-screen request is currently showing, and whether an exchange
 * is on its way to replace it.
 *
 * The two are independent axes on purpose. The case answers what there is to paint — nothing yet, a
 * screen, or a failure — while [isFetching] answers whether the network is busy. A single axis,
 * with a `Loading` case sitting alongside the others, cannot express either of the states this type
 * exists for: a refresh over a screen that is already showing, and a refresh that failed over one.
 * A one-axis state has nowhere to keep the old screen while it says "loading", so the first
 * collapses into a skeleton the user has already moved past and the second blanks a screen that
 * loaded perfectly well.
 *
 * [isFetching] is declared on the interface rather than repeated per case so a caller can read it
 * off whatever it is holding without first establishing which case that is. The cost of the
 * alternative is paid in Swift, where the cases arrive as distinct classes matched by downcast: a
 * per-case declaration would force a three-way match to answer a question that means the same thing
 * in all three.
 *
 * **Cancellation is not a case.** An observation whose collector goes away emits nothing further
 * and never reports a failure the caller did not cause — the same contract
 * [io.jitrapon.astro.data.Result] keeps for one-shot calls.
 *
 * The payload is the whole [CalendarScreenResponse] rather than the [CalendarScreen] inside it. The
 * envelope carries the active theme, the server's own clock, and the resolved time zone and locale;
 * none of those are derivable from the screen and all of them are needed to render it — a client
 * derives "today" from [CalendarScreenResponse.serverTime] and never from the device clock.
 *
 * This is the one name from the observation layer that crosses into Swift. Everything underneath it
 * is internal and stays off the generated framework surface.
 */
sealed interface CalendarScreenQueryState {

    /**
     * Whether an exchange for this request is in flight.
     *
     * True alongside [Loaded] or [Failed] means a refresh is running over what is already showing.
     * It says nothing about what that refresh will produce, and it never invalidates the screen the
     * case carries: whatever a state publishes stays the answer until a later state replaces it.
     */
    val isFetching: Boolean

    /**
     * Nothing has loaded and nothing has failed — where an observation starts.
     *
     * Deliberately distinct from a [Loaded] screen that happens to be empty. A calendar with no
     * events in the requested range is an answer; a caller that needs to tell it apart from "no
     * answer yet" could not if the two shared a case.
     */
    data class Pending(override val isFetching: Boolean) : CalendarScreenQueryState

    /** A screen is showing. */
    data class Loaded(
        /** The screen and the envelope it arrived in. */
        val response: CalendarScreenResponse,
        /**
         * Whether this came from the remembered entry rather than from an exchange.
         *
         * Not a freshness claim: a remembered screen is served precisely so something can be
         * painted while a newer one loads, and whether that is happening is [isFetching]'s answer,
         * not this one's.
         */
        val servedFromCache: Boolean,
        override val isFetching: Boolean,
    ) : CalendarScreenQueryState

    /**
     * The most recent exchange failed.
     *
     * Reaching this case does not discard what had already loaded — [lastLoadedResponse] is what
     * makes a failed refresh reportable without blanking the screen behind it.
     */
    data class Failed(
        /**
         * What went wrong: the same exception [io.jitrapon.astro.data.Result.Error] would carry for
         * the equivalent one-shot call. A cancelled observation never arrives here.
         */
        val error: Exception,
        /**
         * The screen that was showing when the failure landed, or `null` when nothing had loaded
         * yet.
         *
         * Its presence is what a caller decides between a full error screen and an error banner
         * over live content by, so it must not be dropped on the way to the UI.
         */
        val lastLoadedResponse: CalendarScreenResponse?,
        override val isFetching: Boolean,
    ) : CalendarScreenQueryState
}
