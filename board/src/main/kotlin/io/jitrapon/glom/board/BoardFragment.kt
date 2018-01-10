package io.jitrapon.glom.board

import android.arch.lifecycle.Observer
import android.support.v4.app.FragmentActivity
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.SimpleItemAnimator
import android.view.View
import android.widget.ProgressBar
import io.jitrapon.glom.base.component.GooglePlaceProvider
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.ui.widget.stickyheader.StickyHeadersLinearLayoutManager
import io.jitrapon.glom.base.util.animate
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.base.util.startActivity
import kotlinx.android.synthetic.main.board_fragment.*

/**
 * Fragment showing the board items in a group
 *
 * @author Jitrapon Tiachunpun
 */
class BoardFragment : BaseFragment() {

    /* this fragment's main ViewModel instance */
    private lateinit var viewModel: BoardViewModel

    /*
     * Google place provider
     */
    private val placeProvider: PlaceProvider by lazy {
        GooglePlaceProvider(lifecycle, activity = activity)
    }

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
//            val poolCount = 6
//            recycledViewPool = RecyclerView.RecycledViewPool().apply {
//                setMaxRecycledViews(BoardItemUiModel.TYPE_EVENT, poolCount)
//                for (i in 0 .. poolCount) {
//                    putRecycledView(adapter.createViewHolder(board_recycler_view, BoardItemUiModel.TYPE_EVENT))
//                }
//            }
        }

        viewModel.loadBoard()
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
                        if (it.shouldLoadPlaceInfo) viewModel.loadPlaceInfo(placeProvider)

                        // if this list is not null, force update specific items
                        if (!it.itemsChangedIndices.isNullOrEmpty()) {
                            it.itemsChangedIndices?.forEach {
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
            startActivity(BoardItemActivity::class.java, {
                putExtra(Const.EXTRA_BOARD_ITEM, it)
            })
        })
    }

    /**
     * Callback for when the board has been manually refreshed
     */
    override fun onRefresh() {
        viewModel.loadBoard()
    }
}