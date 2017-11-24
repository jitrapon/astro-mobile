package io.jitrapon.glom.board

import android.arch.lifecycle.Observer
import android.support.v4.widget.SwipeRefreshLayout
import android.view.View
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

    private lateinit var progressBar: ProgressBar

    companion object {

        fun newInstance(): BoardFragment = BoardFragment()
    }

    override fun getLayoutId() = R.layout.board_fragment

    override fun getSwipeRefreshLayout(): SwipeRefreshLayout? = board_refresh_layout

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        // on refresh board items
        viewModel.getObservableBoardItems().observe(this, Observer {

        })
    }

    override fun onSetupView(view: View) {
        // need to use findViewById() because kotlinx extension cannot reference view from other module
        progressBar = view.findViewById(R.id.board_progressbar)
    }

    override fun onRefresh() {
        viewModel.loadBoard()
    }

    override fun getEmptyLoadingView(): ProgressBar? = progressBar
}