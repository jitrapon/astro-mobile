package io.jitrapon.glom.board

import android.arch.lifecycle.Observer
import android.support.v4.widget.SwipeRefreshLayout
import android.widget.ProgressBar
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.obtainViewModel
import kotlinx.android.synthetic.main.board_fragment.*

/**
 * Fragment showing the board items in a group
 *
 * @author Jitrapon Tiachunpun
 */
class BoardFragment : BaseFragment() {

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
     * Called when the ViewModel needs to be instantiated
     */
    override fun onCreateViewModel() {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.getObservableBoard().observe(this, Observer {

        })
    }

    override fun onRefresh() {
        viewModel.loadBoard()
    }

    override fun getEmptyLoadingView() = board_progressbar as ProgressBar
}