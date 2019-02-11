package io.jitrapon.glom.board.item.event.preference

import android.preference.PreferenceFragment
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.PreferenceFragmentListener
import kotlinx.android.synthetic.main.event_item_preference_fragment.*

const val EVENT_ITEM_PREF_FRAGMENT_TAG = "event_item_preference"

/**
 * Fragment showing list of available customization options for event items in a board
 *
 * Created by Jitrapon
 */
class EventItemPreferenceFragment : BaseFragment(), PreferenceFragmentListener {

    /**
     * this fragment's main ViewModel instance
     */
    private lateinit var viewModel: EventItemPreferenceViewModel

    /**
     * Returns this fragment's XML layout
     */
    override fun getLayoutId() = R.layout.event_item_preference_fragment

    /**
     * Returns the SwipeRefreshLayout in this fragment's XML layout
     */
    override fun getSwipeRefreshLayout(): SwipeRefreshLayout? = event_item_preference_swipe_refresh_layout

    /**
     * Returns the progress bar for when this view is empty
     */
    override fun getEmptyLoadingView() = event_item_preference_progressbar as ProgressBar

    /**
     * Create this fragment's ViewModel instance
     */
    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(EventItemPreferenceViewModel::class.java)
    }

    /**
     * Called when any view must be initialized
     */
    override fun onSetupView(view: View) {
        event_item_preference_recycler_view.apply {
            adapter = EventItemPreferenceAdapter(this@EventItemPreferenceFragment.context!!, viewModel)
            itemAnimator = DefaultItemAnimator()
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.getObservablePreference().observe(this, Observer {
            it?.let { uiModel ->
                when (uiModel.status) {
                    UiModel.Status.SUCCESS, UiModel.Status.EMPTY -> {
                        if (uiModel.preferencesDiffResult == null) {
                            event_item_preference_recycler_view.adapter?.notifyDataSetChanged()
                        }
                        else {
                            uiModel.preferencesDiffResult?.dispatchUpdatesTo(event_item_preference_recycler_view.adapter!!)
                        }
                    }
                    else -> { /* do nothing */ }
                }
            }
        })
    }

    /**
     * Callback for when the board has been manually refreshed
     */
    override fun onRefresh(delayBeforeRefresh: Long) {
        if (delayBeforeRefresh > 0L) {
            delayRun(delayBeforeRefresh) {
                viewModel.loadPreference(true)
            }
        }
        else {
            viewModel.loadPreference(true)
        }
    }

    override fun onSavePreference() {
        viewModel.savePreference()
    }
}
