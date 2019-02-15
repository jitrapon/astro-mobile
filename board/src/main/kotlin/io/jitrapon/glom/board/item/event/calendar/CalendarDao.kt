package io.jitrapon.glom.board.item.event.calendar

import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.preference.CalendarPreference
import io.reactivex.Flowable

/**
 * Entry point into interacting with the Calendar Provider for performing
 * CRUD operations with events
 */
interface CalendarDao {

    fun getEventsSync(calId: String, startSearchTime: Long, endSearchTime: Long? = null): List<EventItem>

    fun getCalendars(): Flowable<CalendarPreference>
}
