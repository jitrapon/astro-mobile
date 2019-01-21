package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.board.item.event.calendar.CalendarEntity
import io.reactivex.Flowable

/**
 * Main entry to the saved preference settings for event items
 *
 * Created by Jitrapon
 */
interface EventItemPreferenceDataSource {

    fun getSyncedCalendars(): Flowable<List<CalendarEntity>>
}
