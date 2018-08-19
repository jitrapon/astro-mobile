package io.jitrapon.glom.base.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import io.jitrapon.glom.R
import io.jitrapon.glom.base.component.GooglePlaceProvider
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.color
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

    /*
     * shared Google place provider
     */
    val placeProvider: PlaceProvider by lazy {
        GooglePlaceProvider(lifecycle, activity = activity)
    }

    override fun onResume() {
        super.onResume()

        // needs to handle inconsistent behavior with livedata
        // https://stackoverflow.com/questions/48364340/inconsistent-behavior-with-livedata-in-android-7-and-pre-android-7
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) onDispatchPendingLiveData()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(getLayoutId(), container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showEmptyLoading(false)
        getSwipeRefreshLayout()?.apply {
            refreshLayout = this
            setOnRefreshListener(onRefreshListener)
            setColorSchemeColors(color(R.color.lightish_red)!!)
        }
        onCreateViewModel(activity!!)
        onSetupView(view)
        onSubscribeToObservables()
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
                is ReloadData -> onRefresh(it.delay)
            }
        }
    }

    /**
     * Swipe refresh listener that is tied to the ViewActionHandler
     */
    private val onRefreshListener by lazy {
        SwipeRefreshLayout.OnRefreshListener {
            onRefresh(0L)
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
    open fun onRefresh(delayBeforeRefresh: Long) {}

    /**
     * Called when a ViewModel needs to be initialized for use later
     */
    open fun onCreateViewModel(activity: FragmentActivity) {}

    /**
     * Override this method to perform all necessary view initializations in the fragment, if any
     */
    open fun onSetupView(view: View) {}

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
     * Override to allow child class to know that it can begin dispatching LiveData to observe changes
     */
    open fun onDispatchPendingLiveData() {}

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
     * Indicates that the view has no data and should be showing the main loading progress bar.
     * Override this function to make it behave differently
     */
    open fun showEmptyLoading(show: Boolean) {
        getEmptyLoadingView()?.let {
            it.visibility = if (show) View.VISIBLE else View.GONE

            // if triggered manually by swipe refresh layout, hide it. We don't need to show
            // two loading icons
            if (show) showLoading(false)
        }
        // if somehow the refreshlayout is still loading, set it to hide
        if (!show) showLoading(false)
    }

    /**
     * Returns a loading progress bar shown when the view is empty and is about to load a data
     */
    open fun getEmptyLoadingView(): ProgressBar? = null

    /**
     * Overrides this function to allow handling of navigation events
     */
    open fun navigate(action: String, payload: Any?) {}

    /**
     * Wrapper around Android's handler to delay run a Runnable on the main thread
     */
    inline fun delayRun(delay: Long, crossinline block: (Handler) -> Unit) {
        handler.postDelayed({
            block(handler)
        }, delay)
    }
}
