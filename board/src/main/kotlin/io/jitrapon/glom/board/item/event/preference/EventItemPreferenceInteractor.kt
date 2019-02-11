package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.util.AppLogger
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Interactor for querying and updating event item preferences
 *
 * Created by Jitrapon
 */
class EventItemPreferenceInteractor(private val repository: EventItemPreferenceDataSource) : BaseInteractor() {

    val preference: EventItemPreference
            get() = repository.getPreference(false).blockingFirst()

    fun loadPreference(refresh: Boolean, onComplete: (AsyncResult<Pair<Date, EventItemPreference>>) -> Unit) {
        repository.getPreference(refresh)
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

    fun savePreference(onComplete: (AsyncResult<EventItemPreference>) -> Unit) {
        repository.savePreference(preference)
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

    fun getCalendarSyncListDiff() = repository.getCalendarSyncListDiff()

    fun clearCalendarSyncListDiff() {
        repository.clearCalendarSyncListDiff()
    }
}
