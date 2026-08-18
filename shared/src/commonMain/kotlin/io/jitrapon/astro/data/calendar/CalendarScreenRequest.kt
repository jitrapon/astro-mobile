package io.jitrapon.astro.data.calendar

/**
 * Everything one calendar screen fetch is parameterized by.
 *
 * This is also the screen's request identity: two fetches that differ in any field here ask for a
 * different screen, so anything that ever caches or de-duplicates screens must key on the whole
 * object and not on a subset of it.
 */
data class CalendarScreenRequest(
    val view: RequestedCalendarView,
    /** Inclusive first date of the requested window. */
    val start: CalendarDate,
    /** Inclusive last date of the requested window. */
    val end: CalendarDate,
    /** IANA zone id the server formats every date and time in, e.g. `Asia/Bangkok`. */
    val timeZone: String,
    /** BCP-47 tag the server formats every string in, e.g. `th-TH`. */
    val locale: String,
    /**
     * The `id@version` of the theme document the caller already holds, or `null` to always be sent
     * the full document.
     *
     * Naming it lets the server omit `themeDocument` from the response when it is already current.
     * Always the full `id@version` pair, never the id alone: a republished built-in theme keeps its
     * id, and a client that matched on id alone would keep painting stale tokens.
     */
    val knownTheme: String? = null,
)

/**
 * The calendar view being requested, and the day count that only one of them carries.
 *
 * The wire values here are lower-case (`timegrid`) while the response's [CalendarViewSelection]
 * discriminators are camel-case (`timeGrid`). They are two separate vocabularies that the contract
 * declares separately — request and response — and collapsing them into one would put the wrong
 * spelling on one side of the exchange.
 *
 * This enumerates every view the contract's `view` parameter accepts, which is deliberately wider
 * than what [CalendarBody] can decode: only the month and agenda bodies are modelled, so asking for
 * [TimeGrid] or [Year] yields a response that fails to decode and reaches the caller as an error.
 * That is the intended signal until those bodies exist — see [CalendarBody] — rather than a request
 * surface that happens to over-promise. Whoever builds one of those views adds its body branch.
 */
sealed interface RequestedCalendarView {

    /** The value sent as the `view` query parameter. */
    val wireValue: String

    data object Month : RequestedCalendarView {
        override val wireValue: String = "month"
    }

    data object Agenda : RequestedCalendarView {
        override val wireValue: String = "agenda"
    }

    /**
     * The column-based views, which differ from each other only in how many days they show — 1 to 5
     * days for the N-day views, 7 for the week view.
     *
     * [dayCount] lives on this branch rather than on the request because it means nothing to the
     * other views: the server ignores it, and modelling it here makes a month request that carries
     * one unrepresentable rather than merely useless.
     */
    data class TimeGrid(val dayCount: Int) : RequestedCalendarView {

        init {
            require(dayCount in MIN_DAY_COUNT..MAX_DAY_COUNT) {
                "A time grid shows $MIN_DAY_COUNT-$MAX_DAY_COUNT days; was $dayCount."
            }
        }

        override val wireValue: String = "timegrid"

        private companion object {
            const val MIN_DAY_COUNT = 1
            const val MAX_DAY_COUNT = 7
        }
    }

    data object Year : RequestedCalendarView {
        override val wireValue: String = "year"
    }

    companion object {
        /**
         * Every view this client can ask for, one per value the contract's `view` parameter
         * enumerates. [TimeGrid] stands in with its narrowest day count — the branch is one view
         * whatever the count.
         */
        val ALL: List<RequestedCalendarView> = listOf(Month, Agenda, TimeGrid(dayCount = 1), Year)
    }
}

/**
 * A calendar date with no time and no offset, which is what the window boundaries of a screen
 * request are.
 *
 * A date-only type rather than a string or an instant because those are the two ways this parameter
 * goes wrong on the wire: an instant sent where a date is expected is rejected by the server, and a
 * string lets the caller decide the encoding, so the client could not guarantee one. It
 * deliberately carries no calendar arithmetic — when the app takes on a date library, this becomes
 * a thin conversion at the request boundary rather than something to migrate off.
 */
data class CalendarDate(val year: Int, val month: Int, val dayOfMonth: Int) {

    init {
        require(year in MIN_YEAR..MAX_YEAR) { "Year $year is outside $MIN_YEAR..$MAX_YEAR." }
        require(month in MIN_MONTH..MAX_MONTH) { "Month $month is outside $MIN_MONTH..$MAX_MONTH." }
        require(dayOfMonth in MIN_DAY..MAX_DAY) {
            "Day of month $dayOfMonth is outside $MIN_DAY..$MAX_DAY."
        }
    }

    /** Renders `YYYY-MM-DD` — the calendar-date form, with no time component and no zone offset. */
    fun toIsoDate(): String =
        "${year.toString().padStart(YEAR_DIGITS, '0')}-" +
            "${month.toString().padStart(MONTH_DAY_DIGITS, '0')}-" +
            dayOfMonth.toString().padStart(MONTH_DAY_DIGITS, '0')

    private companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 9999
        const val MIN_MONTH = 1
        const val MAX_MONTH = 12
        const val MIN_DAY = 1
        // Not the length of the given month: this type holds no calendar, and rejecting the 31st of
        // a 30-day month is the server's answer to give, not a client-side guess.
        const val MAX_DAY = 31
        const val YEAR_DIGITS = 4
        const val MONTH_DAY_DIGITS = 2
    }
}
