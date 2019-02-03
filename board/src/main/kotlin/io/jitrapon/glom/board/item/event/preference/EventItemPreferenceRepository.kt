package io.jitrapon.glom.board.item.event.preference

import io.reactivex.Flowable
import java.util.*

class EventItemPreferenceRepository : EventItemPreferenceDataSource {

    private var inMemoryPreference: EventItemPreference? = null

    override fun savePreference(preference: EventItemPreference): Flowable<EventItemPreference> {
        return Flowable.fromCallable {
            inMemoryPreference = preference
            inMemoryPreference
        }
    }

    override fun getPreference(refresh: Boolean): Flowable<EventItemPreference> {
        return inMemoryPreference.let {
            if (it == null) Flowable.just(EventItemPreference(CalendarPreference(ArrayList())))
            else Flowable.just(inMemoryPreference)
        }
    }

    override fun getSyncTime(): Date {
        return Date()
    }

    override fun setCalendarSyncStatus(calId: Long, isSynced: Boolean) {
        inMemoryPreference?.calendarPreference?.calendars?.find { it.calId == calId }?.let {
            it.isSyncedToBoard = true
        }
    }
}
