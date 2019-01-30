package io.jitrapon.glom.board.item.event.calendar

import io.reactivex.Flowable

/**
 * Entry point into interacting with the Calendar Provider for performing
 * CRUD operations with events
 */
interface CalendarDao {

    fun getEvents(): Flowable<List<DeviceEvent>>

    fun getCalendars(): Flowable<List<DeviceCalendar>>
}
