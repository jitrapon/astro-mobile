package io.jitrapon.glom.board

import android.arch.lifecycle.Observer
import android.support.v4.widget.SwipeRefreshLayout
import android.widget.ProgressBar
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.obtainViewModel
import kotlinx.android.synthetic.main.board_fragment.*
import kotlinx.android.synthetic.main.loading_indicator.*

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

    override fun getLayoutId() = R.layout.board_fragment

    override fun getSwipeRefreshLayout(): SwipeRefreshLayout? = refresh_layout

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        // on refresh board items
        viewModel.getObservableBoardItems().observe(this, Observer {

        })
    }

    override fun onRefresh() {
        viewModel.loadBoard()
    }

    override fun getEmptyLoadingView(): ProgressBar? = loading_indicator
}