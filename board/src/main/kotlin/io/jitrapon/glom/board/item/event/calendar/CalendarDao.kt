package io.jitrapon.glom.board.item.event.calendar

import io.jitrapon.glom.base.model.NoCalendarPermissionException
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.preference.CalendarPreference
import io.reactivex.Flowable

/**
 * Entry point into interacting with the Calendar Provider for performing
 * CRUD operations with events
 */
interface CalendarDao {

    @Throws(NoCalendarPermissionException::class)
    fun getEventsSync(calendars: List<DeviceCalendar>, startSearchTime: Long, endSearchTime: Long? = null): List<EventItem>

    fun getCalendars(): Flowable<CalendarPreference>

    @Throws(NoCalendarPermissionException::class)
    fun getCalendars(calendarIds: List<String>): List<DeviceCalendar>
}
