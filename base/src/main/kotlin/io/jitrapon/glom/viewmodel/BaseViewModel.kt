package io.jitrapon.glom.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import io.jitrapon.glom.R
import io.jitrapon.glom.data.*
import io.jitrapon.glom.util.run

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
    protected val observableViewAction = LiveEvent<UiActionModel>()

    init {
        observableViewAction.value = EmptyLoading(false)
    }

    /**
     * Returns the observable view actions to be observed by the View
     */
    fun getObservableViewAction(): LiveData<UiActionModel> = observableViewAction

    /**
     * Generic load function to execute long running blocking operation.
     * Supports automatically showing loading progressbar for convenience
     */
    fun <T> runBlockingIO(function: ((AsyncResult<T>) -> Unit) -> Unit, onComplete: (AsyncResult<T>) -> Unit) {
        if (isViewEmpty()) observableViewAction.value = EmptyLoading(true)
        else observableViewAction.value = Loading(true)
        function {
            arrayOf({
                if (isViewEmpty()) observableViewAction.value = EmptyLoading(false)
                else observableViewAction.value = Loading(false)
            }, {
                onComplete(it)
            }).run()
        }
    }

    /**
     * Generic error handling from a response
     */
    fun handleError(throwable: Throwable) {
        observableViewAction.execute(arrayOf(
                Loading(false),
                Snackbar(resId = R.string.error_generic)
        ))
    }

    /**
     * Returns true if the current view has no data to show
     */
    abstract fun isViewEmpty(): Boolean
}