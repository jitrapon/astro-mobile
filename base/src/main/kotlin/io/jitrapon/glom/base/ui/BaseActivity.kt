package io.jitrapon.glom.base.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import com.google.android.instantapps.InstantApps
import io.jitrapon.glom.base.model.Alert
import io.jitrapon.glom.base.model.Snackbar
import io.jitrapon.glom.base.model.Toast
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.base.util.showAlertDialog
import io.jitrapon.glom.base.util.showSnackbar
import io.jitrapon.glom.base.util.showToast

/**
 * Wrapper around Android's AppCompatActivity. Contains convenience functions
 * relating to fragment transactions, activity transitions, Android's new runtime permission handling,
 * analytics, and more. All activities should extend from this class.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseActivity : AppCompatActivity() {

    /* subclass should overwrite this variable for naming the activity */
    var tag: String = "base"

    /* indicates whether or not this Activity instance is Instant App */
    val isInstantApp: Boolean by lazy {
        InstantApps.isInstantApp(this)
    }

    /* shared handler object */
    val handler: Handler by lazy {
        Handler()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onCreateViewModel()
        onSubscribeToObservables()

        // if we're being restored from a previous state,
        // then we don't need to do anything and should return or else
        // we could end up with overlapping fragments
        savedInstanceState ?: onCreateFragment(savedInstanceState)
    }

    /**
     * Common UI Action handlers for all child activities
     */
    private val viewActionHandler: Observer<UiActionModel> = Observer {
        it?.let {
            when (it) {
                is Toast -> showToastMessage(it.message)
                is Snackbar -> showSnackbarMessage(it.message, it.resId, it.actionMessage, it.actionCallback)
                is Alert -> showAlertMessage(it.title, it.message, it.positiveOptionText, it.onPositiveOptionClicked,
                        it.negativeOptionText, it.onNegativeOptionClicked, it.isCancelable, it.onCancel)
            }
        }
    }

    /**
     * Called when one or more instances of fragments have to be created.
     * Only called if savedInstanceState is NULL
     */
    open fun onCreateFragment(savedInstanceState: Bundle?) {}

    /**
     * Called when one or more ViewModel instances should be created
     */
    open fun onCreateViewModel() {}

    /**
     * Subscribe to LiveData and LiveEvent from the ViewModel
     */
    open fun onSubscribeToObservables() {}

    /**
     * Should be called by child class to handle all view action observables automatically
     */
    protected fun subscribeToViewActionObservables(observableViewAction: LiveData<UiActionModel>) {
        observableViewAction.observe(this, viewActionHandler)
    }

    /**
     * Shows a simple toast message. Override this function to make it behave differently
     */
    open fun showToastMessage(message: String?) {
        showToast(message)
    }

    /**
     * Shows a snackbar message. Override this function to make it behave differently
     */
    open fun showSnackbarMessage(message: String?, resId: Int?, actionMessage: String?, actionCallback: (() -> Unit)? = null) {
        showSnackbar(message, resId, actionMessage, actionCallback)
    }

    /**
     * Shows an alert message. Override this function to make it behave differently
     */
    open fun showAlertMessage(title: String?, message: String?, positiveOptionText: String?,
                              onPositiveOptionClicked: (() -> Unit)?, negativeOptionText: String?,
                              onNegativeOptionClicked: (() -> Unit)?, cancelable: Boolean, onCancel: (() -> Unit)?) {
        showAlertDialog(title, message, positiveOptionText, onPositiveOptionClicked,
                negativeOptionText, onNegativeOptionClicked, cancelable, onCancel)
    }

    /**
     * Wrapper around Android's handler to delay run a Runnable on the main thread
     */
    fun delayRun(delay: Long, block: (Handler) -> Unit) {
        handler.postDelayed({
            block(handler)
        }, delay)
    }
}