package io.jitrapon.glom.board.item.event.preference

import io.reactivex.Flowable
import java.util.*
import java.util.concurrent.TimeUnit

class EventItemPreferenceRepository : EventItemPreferenceDataSource {

    override fun getPreference(refresh: Boolean): Flowable<EventItemPreference> {
        return Flowable.just(EventItemPreference(ArrayList())).delay(if (refresh) 500L else 0L, TimeUnit.MILLISECONDS)
    }

    override fun getSyncTime(): Date {
        return Date()
    }
}
