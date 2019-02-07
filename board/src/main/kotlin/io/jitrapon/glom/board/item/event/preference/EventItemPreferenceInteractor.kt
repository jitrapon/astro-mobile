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

    val preference: EventItemPreference
            get() = repository.getPreference(false).blockingFirst()

    fun loadPreference(refresh: Boolean, onComplete: (AsyncResult<Pair<Date, EventItemPreference>>) -> Unit) {
        Flowable.zip(
                calendarDao.getCalendars().subscribeOn(Schedulers.io()),
                repository.getPreference(refresh).subscribeOn(Schedulers.io()),
                BiFunction<CalendarPreference, EventItemPreference, EventItemPreference> { localCalendars, preference ->
                    val calendars = preference.calendarPreference.calendars.associateBy({it.calId}, {it}).let { map ->
                        val result = ArrayList<DeviceCalendar>(preference.calendarPreference.calendars)
                        for (calendar in localCalendars.calendars) {
                            map[calendar.calId].let {

                                // add a local calendar to our result only
                                // if it's not in the repository's list
                                if (it == null) {
                                    calendar.isLocal = true
                                    result.add(calendar)
                                }

                                // otherwise, if it's already in the repository
                                // set the flags correctly
                                else {
                                    it.isLocal = true
                                }
                            }
                        }
                        result
                    }
                    EventItemPreference(
                            CalendarPreference(calendars, Date(), localCalendars.error),
                            repository.getSyncTime())
                })
                .retryWhen(::errorIsUnauthorized)
                .flatMap(repository::savePreference)
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

    fun setCalendarSyncStatus(id: String?, isSynced: Boolean) {
        try {
            id?.toLong()
        }
        catch (ex: Exception) {
            null
        }?.let { calId ->
            repository.setCalendarSyncStatus(calId, isSynced)
        }
    }
}
