package io.jitrapon.astro.data.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The complete returned screen: identity, chrome, resolved preferences, and the typed body. */
@Serializable
data class CalendarScreen(
    val id: String,
    val titleToken: String? = null,
    /** Contextual page heading, formatted by the server for the response's locale. */
    val title: String,
    val navigation: Navigation,
    val viewSwitcher: ViewSwitcher,
    val resolvedPreferences: ResolvedPreferences,
    val body: CalendarBody,
)

/**
 * Semantic, platform-agnostic product destinations. Web renders a sidebar and mobile renders bottom
 * tabs from the same list. Calendar views are NOT destinations — they live on [ViewSwitcher].
 */
@Serializable data class Navigation(val destinations: List<NavDestination>)

/** One product destination. */
@Serializable
data class NavDestination(
    val id: String,
    val labelToken: String? = null,
    val label: String,
    val iconToken: String? = null,
    val action: Action,
)

/** The calendar view chooser: which view is active, and what the user can switch to. */
@Serializable
data class ViewSwitcher(
    val activeSelection: CalendarViewSelection,
    val options: List<ViewSwitcherOption>,
)

/** One offered view, pairing its label with the selection choosing it produces. */
@Serializable
data class ViewSwitcherOption(
    val id: String,
    val label: String,
    val labelToken: String? = null,
    val selection: CalendarViewSelection,
)

/**
 * Which calendar view is selected, typed so "3 Days" and "Week" stay distinguishable — both are
 * time grids and differ only in [TimeGridViewSelection.dayCount].
 *
 * These discriminator values are camel-case (`timeGrid`) while the `view` query parameter the
 * client sends is lower-case (`timegrid`). They are separate vocabularies declared separately by
 * the contract; unifying them would make one of the two wrong on the wire.
 */
@Serializable sealed interface CalendarViewSelection

@Serializable @SerialName("agenda") data object AgendaViewSelection : CalendarViewSelection

@Serializable
@SerialName("timeGrid")
data class TimeGridViewSelection(val dayCount: Int) : CalendarViewSelection

@Serializable @SerialName("month") data object MonthViewSelection : CalendarViewSelection

@Serializable @SerialName("year") data object YearViewSelection : CalendarViewSelection

/**
 * User UI preferences resolved server-side so web and mobile render consistently.
 *
 * Geometry — grid sizing, lane-packing, segmentation, drag snap, overflow slicing — is deliberately
 * absent: it is client-owned.
 *
 * There is deliberately NO `theme` field here. The active theme is identified by
 * [CalendarScreenResponse.theme] on the envelope, because it must be answerable on screen responses
 * that carry no preferences block at all — nesting the reference here once left such a screen with
 * no way to discover its theme. Do not re-add it.
 */
@Serializable
data class ResolvedPreferences(
    /** The client orders its grid from this. It is never sent in a request. */
    val weekStart: WeekStart,
    val chipStyle: ChipStyle,
    val chipDensity: ChipDensity,
)

/** The weekday a calendar grid starts on. */
@Serializable
enum class WeekStart {
    @SerialName("monday") MONDAY,
    @SerialName("sunday") SUNDAY,
}

/**
 * Fill treatment for the filled-bar presentations only (month and time-grid all-day bars):
 * [ACCENT_EDGE] is a solid left edge in the calendar's accent color, [PASTEL] has no edge and only
 * the low-opacity background. It does not apply to the month timed marker, time-grid blocks, or
 * agenda cards.
 */
@Serializable
enum class ChipStyle {
    @SerialName("accentEdge") ACCENT_EDGE,
    @SerialName("pastel") PASTEL,
}

/**
 * The user's chosen density preset paired with the server's resolution of it.
 *
 * The two are related but distinct, and each is authoritative for exactly one thing: [level] for
 * WHICH preset is active (what a settings UI binds to), [maxSubtitleLines] for HOW MANY subtitle
 * lines to render. The preset-to-cap mapping may change server-side without [level] changing, so
 * clients must not re-derive either value from the other.
 */
@Serializable data class ChipDensity(val level: ChipDensityLevel, val maxSubtitleLines: Int)

/** The density presets a settings UI offers. */
@Serializable
enum class ChipDensityLevel {
    @SerialName("compact") COMPACT,
    @SerialName("comfortable") COMFORTABLE,
    @SerialName("detailed") DETAILED,
}
