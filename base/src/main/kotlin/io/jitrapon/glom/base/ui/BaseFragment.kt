package io.jitrapon.glom.base.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v4.app.Fragment
import io.jitrapon.glom.base.data.Alert
import io.jitrapon.glom.base.data.Snackbar
import io.jitrapon.glom.base.data.Toast
import io.jitrapon.glom.base.data.UiActionModel
import io.jitrapon.glom.base.util.showAlertDialog
import io.jitrapon.glom.base.util.showSnackbar
import io.jitrapon.glom.base.util.showToast

/**
 * All fragments should extend from this base class for convenience functions (including analytics).
 * This fragment automatically derives from LifeCycleOwner. Convenience wrapper function
 * checks for null Activity instance for when this Fragment instance is no longer attached to the
 * host activity. This avoids NullPointerExceptions occurring.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseFragment : Fragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        onCreateViewModel()
        subscribeToObservables()
    }

    /**
     * Common UI Action handlers for all child fragments
     */
    private val viewActionHandler: Observer<UiActionModel> = Observer {
        it?.let {
            when (it) {
                is Toast -> showToastMessage(it.message)
                is Snackbar -> showSnackbarMessage(it.message, it.actionMessage, it.actionCallback)
                is Alert -> showAlertMessage(it.title, it.message, it.positiveOptionText, it.onPositiveOptionClicked,
                        it.negativeOptionText, it.onNegativeOptionClicked, it.isCancelable, it.onCancel)
            }
        }
    }

    /**
     * Called when one or more ViewModel instances should be created
     */
    open fun onCreateViewModel() {}

    /**
     * Subscribe to LiveData and LiveEvent from the ViewModel
     */
    open fun subscribeToObservables() {}

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
    open fun showSnackbarMessage(message: String?, actionMessage: String?, actionCallback: (() -> Unit)? = null) {
        showSnackbar(message, actionMessage, actionCallback)
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
}
