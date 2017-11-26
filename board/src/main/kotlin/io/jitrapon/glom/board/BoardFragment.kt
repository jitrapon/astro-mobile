package io.jitrapon.glom.board

import android.arch.lifecycle.Observer
import android.support.v4.widget.SwipeRefreshLayout
import android.view.View
import android.widget.ProgressBar
import io.jitrapon.glom.base.data.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.obtainViewModel
import kotlinx.android.synthetic.main.board_fragment.*

/**
 * Fragment showing the board items in a group
 *
 * @author Jitrapon Tiachunpun
 */
class BoardFragment : BaseFragment() {

    /* this fragment's main ViewModel instance */
    private lateinit var viewModel: BoardViewModel

    companion object {

        fun newInstance(): BoardFragment = BoardFragment()
    }

    /**
     * Returns this fragment's XML layout
     */
    override fun getLayoutId() = R.layout.board_fragment

    /**
     * Returns the SwipeRefreshLayout in this fragment's XML layout
     */
    override fun getSwipeRefreshLayout(): SwipeRefreshLayout? = board_refresh_layout

    /**
     * Returns the progress bar for when this view is empty
     */
    override fun getEmptyLoadingView() = board_progressbar as ProgressBar

    /**
     * Called when the ViewModel needs to be instantiated
     */
    override fun onCreateViewModel() {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    /**
     * Called when any view must be initialized
     */
    override fun onSetupView(view: View) {
        board_recycler_view.apply {

        }
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.getObservableBoard().observe(this, Observer {
            it?.let {
                when (it.status) {
                    UiModel.Status.EMPTY -> board_status_viewswitcher.apply {
                        displayedChild = 0
                    }
                    UiModel.Status.ERROR -> board_status_viewswitcher.apply {
                        displayedChild = 1
                    }
                    UiModel.Status.SUCCESS -> {
                        board_status_viewswitcher.reset()
                        //notify adapter
                    }
                    UiModel.Status.LOADING -> board_status_viewswitcher.reset()
                }
            }
        })
    }

    /**
     * Callback for when the board has been manually refreshed
     */
    override fun onRefresh() {
        viewModel.loadBoard()
    }
}