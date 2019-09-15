package io.jitrapon.glom.board.item.event.calendar

import androidx.annotation.WorkerThread
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
    @WorkerThread
    fun getEventsSync(calendars: List<DeviceCalendar>, startSearchTime: Long, endSearchTime: Long? = null): List<EventItem>

    @WorkerThread
    fun getCalendars(): Flowable<CalendarPreference>

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun getCalendars(calendarIds: List<String>): List<DeviceCalendar>

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun updateEvent(event: EventItem, calendar: DeviceCalendar? = null)

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun createEvent(event: EventItem, calendar: DeviceCalendar)

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun deleteEvent(event: EventItem)
}
