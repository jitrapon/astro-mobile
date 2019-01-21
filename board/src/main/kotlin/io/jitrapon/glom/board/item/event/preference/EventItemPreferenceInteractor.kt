package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.board.item.event.calendar.CalendarDao
import io.jitrapon.glom.board.item.event.calendar.CalendarEntity
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers

/**
 * Interactor for querying and updating event item preferences
 *
 * Created by Jitrapon
 */
class EventItemPreferenceInteractor(private val calendarDao: CalendarDao,
                                    private val repository: EventItemPreferenceDataSource) : BaseInteractor() {

    fun loadCalendars(onComplete: (AsyncResult<List<CalendarEntity>>) -> Unit) {
        Flowable.zip(calendarDao.getCalendars().subscribeOn(Schedulers.io()),
            repository.getSyncedCalendars().subscribeOn(Schedulers.io()),
            BiFunction<List<CalendarEntity>, List<CalendarEntity>, List<CalendarEntity>> { allLocalCalendars, syncedCalendars ->
                // return a single list of calendars with sync status and local field updated
                allLocalCalendars.associateBy({it.calId}, {it}).let { map ->
                    val result = ArrayList<CalendarEntity>(allLocalCalendars)
                    for (synced in syncedCalendars) {
                        map[synced.calId].let {
                            synced.isLocal = it != null
                            synced.isSyncedToBoard = true
                        }
                        result.add(synced)
                    }
                    result
                }
            })
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //nothing yet
            }).autoDispose()
    }
}
