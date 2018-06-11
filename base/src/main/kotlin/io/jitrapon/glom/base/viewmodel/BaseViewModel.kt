package io.jitrapon.glom.base.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.os.Build
import android.os.Looper
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.withinDuration
import java.util.*

/* time in seconds before data is refreshed automatically */
const val REFRESH_INTERVAL = 60

/**
 * Base class for all ViewModel classes. The ViewModel responsibility is to delegate logic
 * to retrieve and modify data to an interactor, then transforms the received DataModel
 * to a UiModel that is observed by the view.
 *
 * Created by Jitrapon
 */

abstract class BaseViewModel : ViewModel() {

    /* Subclass of this class should set appropriate UiActionModel to this variable to emit action to the view
        this can be done by setting its value directly, or calling the run() function to run
        a series of actions */
    val observableViewAction = LiveEvent<UiActionModel>()

    /* List that caches LiveData instances that have not been dispatched to the observer because
       there are no active observers
     */
    private val undispatchedLiveDataList = ArrayList<LiveData<*>>()

    init {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            observableViewAction.value = EmptyLoading(false)
        }
    }

    /**
     * Returns the observable view actions to be observed by the View
     */
    fun getObservableViewAction(): LiveData<UiActionModel> = observableViewAction

    /**
     * Generic load function to execute long running blocking operation.
     * Supports automatically showing loading progressbar for convenience
     */
    inline fun <reified T> loadData(refresh: Boolean, function: (Boolean, (AsyncResult<Pair<Date, T>>) -> Unit) -> Unit,
                                    callbackDelay: Long = 150L, crossinline onComplete: (AsyncResult<Pair<Date, T>>) -> Unit) {
        if (isViewEmpty()) observableViewAction.value = EmptyLoading(true)
        else observableViewAction.value = Loading(true)
        function(refresh) {
            arrayOf({
                if (isViewEmpty()) observableViewAction.value = EmptyLoading(false)
                else observableViewAction.value = Loading(false)
            }, {
                onComplete(it)

                // if loading is successful, check if data is stale
                // if it is, force refresh
                if (it is AsyncSuccessResult && !it.result.first.withinDuration(Date(), REFRESH_INTERVAL)) {
                    observableViewAction.value = ReloadData(100L)
                }
            }).run(callbackDelay)
        }
    }

    /**
     * Dispatches any pending live data waiting because the observer was not active.
     * Should be called when the Observer changes its state to ACTIVE
     */
    fun dispatchPendingLiveData() {
        undispatchedLiveDataList.apply {
            forEach {
                if (it is MutableLiveData) it.value = it.value
            }
            clear()
        }
    }

    /**
     * Add the specified LiveData instance to the undispatched list. If the Observer changes to active state,
     * it will be dispatched automatically. Only applicable to Android version less than 7.0
     */
    fun addLiveDataToUndispatchedList(liveData: LiveData<*>) {
       if (!liveData.hasActiveObservers() && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
           undispatchedLiveDataList.add(liveData)
       }
    }

    /**
     * Generic error handling from a response
     */
    fun handleError(throwable: Throwable) {
        AppLogger.e(throwable)
        observableViewAction.execute(arrayOf(
                Loading(false),
                Snackbar(AndroidString(R.string.error_generic), level = MessageLevel.ERROR)
        ))
    }

    /**
     * Returns true if the current view has no data to show
     */
    abstract fun isViewEmpty(): Boolean
}