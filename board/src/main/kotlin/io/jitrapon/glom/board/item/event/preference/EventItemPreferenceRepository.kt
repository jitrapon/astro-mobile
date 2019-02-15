package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.base.model.SimpleDiffResult
import io.jitrapon.glom.board.item.event.CalendarEntity
import io.reactivex.Flowable
import java.util.*

class EventItemPreferenceRepository(private val localDataSource: EventItemPreferenceLocalDataSource) : EventItemPreferenceDataSource {

    override fun savePreference(preference: EventItemPreference): Flowable<EventItemPreference> {
        return localDataSource.savePreference(preference)
    }

    override fun getPreference(refresh: Boolean): Flowable<EventItemPreference> {
        return localDataSource.getPreference(refresh)
    }

    override fun getSyncTime(): Date {
        return localDataSource.getSyncTime()
    }

    override fun setCalendarSyncStatus(calId: Long, isSynced: Boolean) {
        localDataSource.setCalendarSyncStatus(calId, isSynced)
    }

    override fun getCalendarDiff(): SimpleDiffResult<String> = localDataSource.getCalendarDiff()

    override fun clearCalendarDiff() {
        localDataSource.clearCalendarDiff()
    }

    override fun getSyncedCalendars(): Flowable<List<CalendarEntity>> {
        return localDataSource.getSyncedCalendars()
    }
}
