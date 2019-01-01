package io.jitrapon.glom.board.item.event

import io.reactivex.Flowable

/**
 * Entry point into interacting with the Calendar Provider for performing
 * CRUD operations with events
 */
interface CalendarDao {

    fun getEvents(): Flowable<List<EventEntity>>

    fun getCalendars(): Flowable<List<CalendarEntity>>
}
