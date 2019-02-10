package io.jitrapon.glom.board.item.event.preference

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
}
