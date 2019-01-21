package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.board.item.event.calendar.CalendarEntity
import io.reactivex.Flowable

class EventItemPreferenceRepository : EventItemPreferenceDataSource {

    override fun getSyncedCalendars(): Flowable<List<CalendarEntity>> {
        return Flowable.just(ArrayList())
    }
}
