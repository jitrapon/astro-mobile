package io.jitrapon.glom.base.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.jitrapon.glom.base.data.*
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

    /* this fragment's swipe refresh layout, if provided */
    private var refreshLayout: SwipeRefreshLayout? = null

    /* shared handler object */
    val handler: Handler by lazy {
        Handler()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(getLayoutId(), container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        getSwipeRefreshLayout()?.apply {
            refreshLayout = this
            setOnRefreshListener(onRefreshListener)
        }
        onSetupView(view)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        onCreateViewModel()
        onSubscribeToObservables()
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
                is Loading -> showLoading(it.show)
            }
        }
    }

    /**
     * Swipe refresh listener that is tied to the ViewActionHandler
     */
    private val onRefreshListener by lazy {
        SwipeRefreshLayout.OnRefreshListener {
            onRefresh()
        }
    }

    /**
     * Child fragment class must return the layout ID resource to be inflated
     */
    abstract fun getLayoutId(): Int

    /**
     * Child fragment class should override this to indicate that this fragment is swipe-refreshable and
     * contains a SwipeRefreshLayout in its layout xml file. Default to NULL
     */
    open fun getSwipeRefreshLayout(): SwipeRefreshLayout? = null

    /**
     * Called when a RefreshLayout has been triggered manually by the user. This is a good time
     * to call any necessary ViewModel's function to (re)-load the data
     */
    open fun onRefresh() {}

    /**
     * Override this method to perform all necessary view initializations in the fragment, if any
     */
    open fun onSetupView(view: View) {}

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

    /**
     * Indicates that the view is loading some data. Override this function to make it behave differently
     */
    open fun showLoading(show: Boolean) {
        refreshLayout?.let {
            if (show) {
                if (!it.isRefreshing) it.isRefreshing = true
            }
            else {
                if (it.isRefreshing) it.isRefreshing = false
            }
        }
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
