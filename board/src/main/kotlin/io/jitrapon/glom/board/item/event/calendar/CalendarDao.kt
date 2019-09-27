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
    fun getEventsSync(
        calendars: List<DeviceCalendar>,
        startSearchTime: Long,
        endSearchTime: Long? = null,
        requestSync: Boolean
    ): List<EventItem>

    @WorkerThread
    fun getCalendars(): Flowable<CalendarPreference>

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun getCalendars(calendarIds: List<String>): List<DeviceCalendar>

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun updateEvent(event: EventItem, calendar: DeviceCalendar? = null): Boolean

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun createEvent(event: EventItem, calendar: DeviceCalendar): Boolean

    @Throws(NoCalendarPermissionException::class)
    @WorkerThread
    fun deleteEvent(event: EventItem): Boolean

    @WorkerThread
    fun syncCalendar(calendar: DeviceCalendar)

    /**
     * onChange will be invoked on a background thread
     */
    fun registerUpdateObserver(onContentChange: (selfChange: Boolean) -> Unit)

    fun unregisterUpdateObserver()
}
