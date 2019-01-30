package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.board.item.event.calendar.CalendarDao
import io.jitrapon.glom.board.item.event.calendar.DeviceCalendar
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Interactor for querying and updating event item preferences
 *
 * Created by Jitrapon
 */
class EventItemPreferenceInteractor(private val calendarDao: CalendarDao,
                                    private val repository: EventItemPreferenceDataSource) : BaseInteractor() {

    fun loadPreference(refresh: Boolean, onComplete: (AsyncResult<Pair<Date, EventItemPreference>>) -> Unit) {
        Flowable.zip(
                calendarDao.getCalendars().subscribeOn(Schedulers.io()),
                repository.getPreference(refresh).subscribeOn(Schedulers.io()),
                BiFunction<List<DeviceCalendar>, EventItemPreference, EventItemPreference> { allLocalCalendars, preference ->
                    val calendars = allLocalCalendars.associateBy({it.calId}, {it}).let { map ->
                        val result = ArrayList<DeviceCalendar>(allLocalCalendars)
                        for (synced in preference.calendars) {
                            map[synced.calId].let {
                                synced.isLocal = it != null
                                synced.isSyncedToBoard = true
                            }
                            result.add(synced)
                        }
                        result
                    }
                    EventItemPreference(calendars, repository.getSyncTime())
                })
                .retryWhen(::errorIsUnauthorized)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult( (it.retrievedTime ?: Date()) to it))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                }).autoDispose()
    }
}
