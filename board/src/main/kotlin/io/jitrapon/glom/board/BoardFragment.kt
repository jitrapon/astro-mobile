package io.jitrapon.glom.board

import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.Intent
import android.support.v4.app.FragmentActivity
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SimpleItemAnimator
import android.view.View
import android.widget.ProgressBar
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeadersLinearLayoutManager
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.event.EventItem
import io.jitrapon.glom.board.event.EventItemActivity
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
     * Create this fragment's ViewModel instance
     */
    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(BoardViewModel::class.java)
    }

    /**
     * Called when any view must be initialized
     */
    override fun onSetupView(view: View) {
        board_recycler_view.apply {
            adapter = BoardItemAdapter(viewModel, this@BoardFragment)
            layoutManager = StickyHeadersLinearLayoutManager<BoardItemAdapter>(view.context)
            itemAnimator = DefaultItemAnimator()
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

            // uncomment lines below to pre-create recyclerview viewholders
//            createRecycledPool(this, 15)
        }
        board_animation_view.hide()

        // start loading data
        // we don't force-refresh data because when configuration changes occur,
        // we can reuse the same loaded data
        viewModel.loadBoard(false)
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        // observes board and its items
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

                        // loads additional place information for items that have them
                        it.requestPlaceInfoItemIds.let {
                            when {
                                it == null -> { /* do nothing */ }
                                it.isEmpty() -> viewModel.loadPlaceInfo(placeProvider, null)
                                else -> viewModel.loadPlaceInfo(placeProvider, it)
                            }
                        }

                        // if this list is not null, force update specific items
                        if (!it.itemsChangedIndices.isNullOrEmpty()) {
                            it.itemsChangedIndices?.forEach {
                                AppLogger.i("Observer triggered for notifyItemChanged at index ${it.first}")
                                board_recycler_view.adapter.notifyItemChanged(it.first, it.second)
                            }
                        }
                        else {
                            // perform full recyclerview updates only when diff results is not available
                            it.diffResult.let {
                                if (it == null) {
                                    board_recycler_view.adapter.notifyDataSetChanged()
                                }
                                else {
                                    it.dispatchUpdatesTo(board_recycler_view.adapter)
                                }
                            }
                        }
                    }
                    UiModel.Status.LOADING -> board_status_viewswitcher.reset()
                }
            }
        })

        // observes animation
        viewModel.getObservableAnimation().observe(this, Observer {
            it?.let {
                board_animation_view.animate(it)
            }
        })

        // observes new selected board item
        viewModel.getObservableSelectedBoardItem().observe(this, Observer {
            it?.let { pair ->
                val boardItem = pair.first
                val sharedElements = pair.second
                val launchOption = when (boardItem) {
                    is EventItem -> EventItemActivity::class.java to pair.first
                    else -> null
                }
                launchOption?.let { option ->
                    startActivity(option.first, Const.EDIT_ITEM_RESULT_CODE, {
                        putExtra(Const.EXTRA_BOARD_ITEM, option.second)
                    }, sharedElements)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Const.EDIT_ITEM_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    data?.getParcelableExtra<BoardItem>(Const.EXTRA_BOARD_ITEM)?.let {
                        viewModel.saveItemChanges(it)
                    }
                }
                catch (ex: Exception) {
                    AppLogger.e(ex)
                }
            }
        }
    }

    /**
     * Callback for when the board has been manually refreshed
     */
    override fun onRefresh() {
        viewModel.loadBoard(true)
    }

    /**
     * Experiment: optimize recyclerview by creating viewholders beforehand for better
     * scrolling performance
     */
    private fun createRecycledPool(recyclerView: RecyclerView, poolCount: Int) {
        recyclerView.apply {
            recycledViewPool = RecyclerView.RecycledViewPool().apply {
                setMaxRecycledViews(BoardItemUiModel.TYPE_EVENT, poolCount)
                for (i in 0..poolCount) {
                    putRecycledView(adapter.createViewHolder(board_recycler_view, BoardItemUiModel.TYPE_EVENT))
                }
            }
        }
    }
}