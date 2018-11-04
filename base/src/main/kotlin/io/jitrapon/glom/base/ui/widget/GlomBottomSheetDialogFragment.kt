package io.jitrapon.glom.base.ui.widget

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.showAlertDialog
import io.jitrapon.glom.base.util.showSnackbar
import io.jitrapon.glom.base.util.showToast

/**
 * BottomSheetDialog fragment that uses a custom theme which sets a rounded background to the dialog
 * and doesn't dim the navigation bar.
 *
 * credit: https://gist.github.com/ArthurNagy/1c4a64e6c8a7ddfca58638a9453e4aed
 */
abstract class GlomBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.Theme_Glom_BottomSheet_Dialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = BottomSheetDialog(requireContext(), theme)

    /* shared handler object */
    val handler: Handler by lazy {
        Handler()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contextThemeWrapper = ContextThemeWrapper(activity, R.style.Theme_Glom)
        return inflater.cloneInContext(contextThemeWrapper)
            .inflate(getLayoutId(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        onCreateViewModel(activity!!)
        onSetupView(view)
        onSubscribeToObservables()
    }

    /**
     * Should be called by child class to handle all view action observables automatically
     */
    protected fun subscribeToViewActionObservables(observableViewAction: LiveData<UiActionModel>) {
        observableViewAction.observe(this, viewActionHandler)
    }

    /**
     * Common UI Action handlers for all child fragments
     */
    private val viewActionHandler: Observer<UiActionModel> = Observer {
        it?.let {
            when (it) {
                is Toast -> showToast(it.message)
                is Snackbar -> showSnackbar(it.level, it.message, it.actionMessage, it.actionCallback)
                is Alert -> showAlertDialog(it.title, it.message, it.positiveOptionText, it.onPositiveOptionClicked,
                        it.negativeOptionText, it.onNegativeOptionClicked, it.isCancelable, it.onCancel)
                is Loading -> showLoading(it.show)
                is EmptyLoading -> showEmptyLoading(it.show)
                is Navigation -> navigate(it.action, it.payload)
            }
        }
    }

    /**
     * Indicates that the view is loading some data. Override this function to make it behave differently
     */
    open fun showLoading(show: Boolean) {}

    /**
     * Indicates that the view has no data and should be showing the main loading progress bar.
     * Override this function to make it behave differently
     */
    open fun showEmptyLoading(show: Boolean) {}

    /**
     * Overrides this function to allow handling of navigation events
     */
    open fun navigate(action: String, payload: Any?) {}

    /**
     * Child fragment class must return the layout ID resource to be inflated
     */
    abstract fun getLayoutId(): Int

    /**
     * Called when a ViewModel needs to be initialized for use later
     */
    open fun onCreateViewModel(activity: FragmentActivity) {}

    /**
     * Override this method to perform all necessary view initializations in the fragment, if any
     */
    open fun onSetupView(view: View) {}

    /**
     * Wrapper around Android's handler to delay run a Runnable on the main thread
     */
    inline fun delayRun(delay: Long, crossinline block: (Handler) -> Unit) {
        handler.postDelayed({
            block(handler)
        }, delay)
    }

    /**
     * Subscribe to LiveData and LiveEvent from the ViewModel
     */
    open fun onSubscribeToObservables() {}
}
