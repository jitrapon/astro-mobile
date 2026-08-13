package io.jitrapon.astro.data.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A client action the server attached to an affordance, discriminated on `type`. Each branch
 * carries exactly its own payload, so an action can never arrive with the wrong one.
 */
@Serializable sealed interface Action

/** Go to another product screen, named by its screen id. */
@Serializable @SerialName("navigate") data class NavigateAction(val screen: String) : Action

/** Open an external URL. */
@Serializable @SerialName("openUrl") data class OpenUrlAction(val url: String) : Action

/** Switch the calendar to another view without leaving the screen. */
@Serializable
@SerialName("switchCalendarView")
data class SwitchCalendarViewAction(val selection: CalendarViewSelection) : Action

/** Open the detail screen for one event. */
@Serializable
@SerialName("openEventDetail")
data class OpenEventDetailAction(val eventId: String) : Action

/** Present a modal over the current screen — an overflow "+N more", or a day's events. */
@Serializable
@SerialName("presentModal")
data class PresentModalAction(val eventIds: List<String>) : Action
