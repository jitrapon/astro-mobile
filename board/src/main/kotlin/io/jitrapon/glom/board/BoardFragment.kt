package io.jitrapon.glom.board

import android.support.v4.widget.SwipeRefreshLayout
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

    override fun getLayoutId() = R.layout.board_fragment

    override fun getSwipeRefreshLayout(): SwipeRefreshLayout? = refresh_layout

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())
    }

    override fun onRefresh() {
        delayRun(1000L) {
            viewModel.loadBoard()
        }
    }
}