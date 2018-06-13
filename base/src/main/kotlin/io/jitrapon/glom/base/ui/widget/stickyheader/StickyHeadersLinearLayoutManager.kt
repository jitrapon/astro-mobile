package io.jitrapon.glom.base.ui.widget.stickyheader

import android.content.Context
import android.graphics.PointF
import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewTreeObserver
import io.jitrapon.glom.base.util.AppLogger

/**
 * Adds sticky headers capabilities to your {@link RecyclerView.Adapter}. It must implement {@link StickyHeaders} to
 * indicate which items are headers.
 *
 * @author Google
 */
class StickyHeadersLinearLayoutManager<T> : LinearLayoutManager
        where T : RecyclerView.Adapter<*>, T : StickyHeaders {

    private var adapter: T? = null

    private var translationX: Float = 0f
    private var translationY: Float = 0f

    // Header positions for the currently displayed list and their observer.
    private val headerPositions = ArrayList<Int>(0)
    private val headerPositionsObserver: RecyclerView.AdapterDataObserver = HeaderPositionsAdapterDataObserver()

    // Sticky header's ViewHolder and dirty state.
    private var stickyHeader: View? = null
    private var stickyHeaderPosition: Int = RecyclerView.NO_POSITION

    private var pendingScrollPosition = RecyclerView.NO_POSITION
    private var pendingScrollOffset = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(context, orientation, reverseLayout)

    /**
     * Offsets the vertical location of the sticky header relative to the its default position.
     */
    fun setStickyHeaderTranslationY(dy: Float) {
        translationY = dy
        requestLayout()
    }

    /**
     * Offsets the horizontal location of the sticky header relative to the its default position.
     */
    fun setStickyHeaderTranslationX(dx: Float) {
        translationX = dx
        requestLayout()
    }

    /**
     * Returns true if `view` is the current sticky header.
     */
    fun isStickyHeader(view: View): Boolean {
        return view === stickyHeader
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        setAdapter(view.adapter)
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>, newAdapter: RecyclerView.Adapter<*>) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        setAdapter(newAdapter)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setAdapter(recyclerViewAdapter: RecyclerView.Adapter<*>?) {
        adapter?.unregisterAdapterDataObserver(headerPositionsObserver)

        if (recyclerViewAdapter is StickyHeaders) {
            adapter = recyclerViewAdapter as T
            adapter?.registerAdapterDataObserver(headerPositionsObserver)
            headerPositionsObserver.onChanged()
        }
        else {
            adapter = null
            headerPositions.clear()
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return io.jitrapon.glom.base.ui.widget.stickyheader.SavedState().apply {
            superState = super.onSaveInstanceState()
            pendingScrollPosition = this@StickyHeadersLinearLayoutManager.pendingScrollPosition
            pendingScrollOffset = this@StickyHeadersLinearLayoutManager.pendingScrollOffset
        }
    }

    override fun onRestoreInstanceState(parcel: Parcelable) {
        var state = parcel
        if (parcel is io.jitrapon.glom.base.ui.widget.stickyheader.SavedState) {
            pendingScrollPosition = parcel.pendingScrollPosition
            pendingScrollOffset = parcel.pendingScrollOffset
            state = parcel.superState
        }

        super.onRestoreInstanceState(state)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        detachStickyHeader()
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        attachStickyHeader()

        if (scrolled != 0) {
            updateStickyHeader(recycler, false)
        }

        return scrolled
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        detachStickyHeader()
        val scrolled = super.scrollHorizontallyBy(dx, recycler, state)
        attachStickyHeader()

        if (scrolled != 0) {
            updateStickyHeader(recycler, false)
        }

        return scrolled
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        try {
            detachStickyHeader()
            super.onLayoutChildren(recycler, state)
            attachStickyHeader()

            if (!state.isPreLayout) {
                updateStickyHeader(recycler, true)
            }
        }
        catch (ex: Exception) {
            AppLogger.e(ex)
        }
    }

    override fun scrollToPosition(position: Int) {
        scrollToPositionWithOffset(position, LinearLayoutManager.INVALID_OFFSET)
    }

    override fun scrollToPositionWithOffset(position: Int, offset: Int) {
        scrollToPositionWithOffset(position, offset, true)
    }

    private fun scrollToPositionWithOffset(position: Int, offset: Int, adjustForStickyHeader: Boolean) {
        // Reset pending scroll.
        setPendingScroll(RecyclerView.NO_POSITION, INVALID_OFFSET)

        // Adjusting is disabled.
        if (!adjustForStickyHeader) {
            super.scrollToPositionWithOffset(position, offset)
            return
        }

        // There is no header above or the position is a header.
        val headerIndex = findHeaderIndexOrBefore(position)
        if (headerIndex == -1 || findHeaderIndex(position) != -1) {
            super.scrollToPositionWithOffset(position, offset)
            return
        }

        // The position is right below a header, scroll to the header.
        if (findHeaderIndex(position - 1) != -1) {
            super.scrollToPositionWithOffset(position - 1, offset)
            return
        }

        // Current sticky header is the same as at the position. Adjust the scroll offset and reset pending scroll.
        stickyHeader?.let {
            if (headerIndex == findHeaderIndex(stickyHeaderPosition)) {
                val adjustedOffset = (if (offset != INVALID_OFFSET) offset else 0) + it.height
                super.scrollToPositionWithOffset(position, adjustedOffset)
                return
            }
        }

        // Remember this position and offset and scroll to it to trigger creating the sticky header.
        setPendingScroll(position, offset)
        super.scrollToPositionWithOffset(position, offset)
    }

    override fun computeVerticalScrollExtent(state: RecyclerView.State?): Int {
        detachStickyHeader()
        val extent = super.computeVerticalScrollExtent(state)
        attachStickyHeader()
        return extent
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State?): Int {
        detachStickyHeader()
        val offset = super.computeVerticalScrollOffset(state)
        attachStickyHeader()
        return offset
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State?): Int {
        detachStickyHeader()
        val range = super.computeVerticalScrollRange(state)
        attachStickyHeader()
        return range
    }

    override fun computeHorizontalScrollExtent(state: RecyclerView.State?): Int {
        detachStickyHeader()
        val extent = super.computeHorizontalScrollExtent(state)
        attachStickyHeader()
        return extent
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State?): Int {
        detachStickyHeader()
        val offset = super.computeHorizontalScrollOffset(state)
        attachStickyHeader()
        return offset
    }

    override fun computeHorizontalScrollRange(state: RecyclerView.State?): Int {
        detachStickyHeader()
        val range = super.computeHorizontalScrollRange(state)
        attachStickyHeader()
        return range
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF {
        detachStickyHeader()
        val vector = super.computeScrollVectorForPosition(targetPosition)
        attachStickyHeader()
        return vector
    }

    override fun onFocusSearchFailed(focused: View, focusDirection: Int, recycler: RecyclerView.Recycler,
                                     state: RecyclerView.State): View? {
        detachStickyHeader()
        val view = super.onFocusSearchFailed(focused, focusDirection, recycler, state)
        attachStickyHeader()
        return view
    }

    private fun detachStickyHeader() {
        stickyHeader?.let(this::detachView)
    }

    private fun attachStickyHeader() {
        stickyHeader?.let(this::attachView)
    }

    /**
     * Updates the sticky header state (creation, binding, display), to be called whenever there's a layout or scroll
     */
    private fun updateStickyHeader(recycler: RecyclerView.Recycler, layout: Boolean) {
        val headerCount = headerPositions.size
        if (headerCount > 0 && childCount > 0) {
            // Find first valid child.
            var anchorView: View? = null
            var anchorIndex: Int = -1
            var anchorPos: Int = -1
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val params = child.layoutParams as RecyclerView.LayoutParams
                if (isViewValidAnchor(child, params)) {
                    anchorView = child
                    anchorIndex = i
                    anchorPos = params.viewAdapterPosition
                    break
                }
            }
            if (anchorView != null && anchorPos != -1) {
                val headerIndex = findHeaderIndexOrBefore(anchorPos)
                val headerPos = if (headerIndex != -1) headerPositions[headerIndex] else -1
                val nextHeaderPos = if (headerCount > headerIndex + 1) headerPositions[headerIndex + 1] else -1

                // Show sticky header if:
                // - There's one to show;
                // - It's on the edge or it's not the anchor view;
                // - Isn't followed by another sticky header;
                if (headerPos != -1
                        && (headerPos != anchorPos || isViewOnBoundary(anchorView))
                        && nextHeaderPos != headerPos + 1) {
                    // Ensure existing sticky header, if any, is of correct type.
                    if (stickyHeader != null
                            && getItemViewType(stickyHeader) != adapter?.getItemViewType(headerPos)) {
                        // A sticky header was shown before but is not of the correct type. Scrap it.
                        scrapStickyHeader(recycler)
                    }

                    // Ensure sticky header is created, if absent, or bound, if being laid out or the position changed.
                    if (stickyHeader == null) {
                        createStickyHeader(recycler, headerPos)
                    }
                    if (layout || getPosition(stickyHeader) != headerPos) {
                        bindStickyHeader(recycler, headerPos)
                    }

                    // Draw the sticky header using translation values which depend on orientation, direction and
                    // position of the next header view.
                    var nextHeaderView: View? = null
                    if (nextHeaderPos != -1) {
                        nextHeaderView = getChildAt(anchorIndex + (nextHeaderPos - anchorPos))
                        // The header view itself is added to the RecyclerView. Discard it if it comes up.
                        if (nextHeaderView === stickyHeader) {
                            nextHeaderView = null
                        }
                    }
                    stickyHeader?.apply {
                        translationX = getX(this, nextHeaderView)
                        translationY = getY(this, nextHeaderView)
                    }
                    return
                }
            }
        }

        stickyHeader?.let {
            scrapStickyHeader(recycler)
        }
    }

    /**
     * Creates {@link RecyclerView.ViewHolder} for {@code position}, including measure / layout, and assigns it to
     * {@link #stickyHeader}.
     */
    private fun createStickyHeader(recycler: RecyclerView.Recycler, position: Int) {
        val header = recycler.getViewForPosition(position)

        // Setup sticky header if the adapter requires it.
        if (adapter is StickyHeaders.ViewSetup) (adapter as? StickyHeaders.ViewSetup)?.setupStickyHeaderView(header)

        // Add sticky header as a child view, to be detached / reattached whenever LinearLayoutManager#fill() is called,
        // which happens on layout and scroll (see overrides).
        addView(header)
        measureAndLayout(header)

        // Ignore sticky header, as it's fully managed by this LayoutManager.
        ignoreView(header)

        stickyHeader = header
        stickyHeaderPosition = position
    }

    /**
     * Binds the {@link #stickyHeader} for the given {@code position}.
     */
    private fun bindStickyHeader(recycler: RecyclerView.Recycler, position: Int) {
        // Bind the sticky header.
        recycler.bindViewToPosition(stickyHeader, position)
        stickyHeaderPosition = position
        measureAndLayout(stickyHeader!!)

        // If we have a pending scroll wait until the end of layout and scroll again.
        if (pendingScrollPosition != RecyclerView.NO_POSITION) {
            val vto = stickyHeader!!.viewTreeObserver
            vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    vto.removeOnGlobalLayoutListener(this)

                    if (pendingScrollPosition != RecyclerView.NO_POSITION) {
                        scrollToPositionWithOffset(pendingScrollPosition, pendingScrollOffset)
                        setPendingScroll(RecyclerView.NO_POSITION, LinearLayoutManager.INVALID_OFFSET)
                    }
                }
            })
        }
    }

    /**
     * Measures and lays out `stickyHeader`.
     */
    private fun measureAndLayout(stickyHeader: View) {
        measureChildWithMargins(stickyHeader, 0, 0)
        if (orientation == LinearLayoutManager.VERTICAL) {
            stickyHeader.layout(paddingLeft, 0, width - paddingRight, stickyHeader.measuredHeight)
        }
        else {
            stickyHeader.layout(0, paddingTop, stickyHeader.measuredWidth, height - paddingBottom)
        }
    }

    /**
     * Returns {@link #mStickyHeader} to the {@link RecyclerView}'s {@link RecyclerView.RecycledViewPool}, assigning it
     * to {@code null}.
     *
     * @param recycler If passed, the sticky header will be returned to the recycled view pool.
     */
    private fun scrapStickyHeader(recycler: RecyclerView.Recycler?) {
        val header = stickyHeader
        stickyHeader = null
        stickyHeaderPosition = RecyclerView.NO_POSITION

        // Revert translation values.
        header?.let {
            it.translationX = 0f
            it.translationY = 0f

            // Teardown holder if the adapter requires it.
            if (adapter is StickyHeaders.ViewSetup) {
                (adapter as StickyHeaders.ViewSetup).teardownStickyHeaderView(it)
            }

            // Stop ignoring sticky header so that it can be recycled.
            stopIgnoringView(it)

            // Remove and recycle sticky header.
            removeView(it)
            recycler?.recycleView(header)
        }
    }

    /**
     * Returns true when {@code view} is a valid anchor, ie. the first view to be valid and visible.
     */
    private fun isViewValidAnchor(view: View, params: RecyclerView.LayoutParams ): Boolean {
        if (!params.isItemRemoved && !params.isViewInvalid) {
            return if (orientation == VERTICAL) {
                if (reverseLayout) {
                    view.top + view.translationY <= height + translationY
                } else {
                    view.bottom - view.translationY >= translationY
                }
            }
            else {
                if (reverseLayout) {
                    view.left + view.translationX <= width + translationX
                }
                else {
                    view.right - view.translationX >= translationX
                }
            }
        }
        else {
            return false
        }
    }

    /**
     * Returns true when the `view` is at the edge of the parent [RecyclerView].
     */
    private fun isViewOnBoundary(view: View): Boolean {
        return if (orientation == LinearLayoutManager.VERTICAL) {
            if (reverseLayout) {
                view.bottom - view.translationY > height + translationY
            }
            else {
                view.top + view.translationY < translationY
            }
        }
        else {
            if (reverseLayout) {
                view.right - view.translationX > width + translationX
            }
            else {
                view.left + view.translationX < translationX
            }
        }
    }

    /**
     * Returns the position in the Y axis to position the header appropriately, depending on orientation, direction and
     * [android.R.attr.clipToPadding].
     */
    private fun getY(headerView: View, nextHeaderView: View?): Float {
        return if (orientation == LinearLayoutManager.VERTICAL) {
            var y = translationY
            if (reverseLayout) {
                y += (height - headerView.height).toFloat()
            }
            if (nextHeaderView != null) {
                y = if (reverseLayout) Math.max(nextHeaderView.bottom.toFloat(), y) else Math.min((nextHeaderView.top - headerView.height).toFloat(), y)
            }
            y
        }
        else {
            translationY
        }
    }

    /**
     * Returns the position in the X axis to position the header appropriately, depending on orientation, direction and
     * [android.R.attr.clipToPadding].
     */
    private fun getX(headerView: View, nextHeaderView: View?): Float {
        return if (orientation != LinearLayoutManager.VERTICAL) {
            var x = translationX
            if (reverseLayout) {
                x += (width - headerView.width).toFloat()
            }
            if (nextHeaderView != null) {
                x = if (reverseLayout) Math.max(nextHeaderView.right.toFloat(), x) else Math.min((nextHeaderView.left - headerView.width).toFloat(), x)
            }
            x
        }
        else {
            translationX
        }
    }

    /**
     * Finds the header index of `position` in `mHeaderPositions`.
     */
    private fun findHeaderIndex(position: Int): Int {
        var low = 0
        var high = headerPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            when {
                headerPositions[middle] > position -> high = middle - 1
                headerPositions[middle] < position -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }

    /**
     * Finds the header index of `position` or the one before it in `mHeaderPositions`.
     */
    private fun findHeaderIndexOrBefore(position: Int): Int {
        var low = 0
        var high = headerPositions.size - 1
        while (low <= high) {
            val middle = (low + high) / 2
            if (headerPositions[middle] > position) {
                high = middle - 1
            } else if (middle < headerPositions.size - 1 && headerPositions[middle + 1] <= position) {
                low = middle + 1
            } else {
                return middle
            }
        }
        return -1
    }

    private fun setPendingScroll(position: Int, offset: Int) {
        pendingScrollPosition = position
        pendingScrollOffset = offset
    }

    /**
     * Handles header positions while adapter changes occur.
     *
     * This is used in detriment of {@link RecyclerView.LayoutManager}'s callbacks to control when they're received.
     *
     * @author Google
     */
    private inner class HeaderPositionsAdapterDataObserver : RecyclerView.AdapterDataObserver() {

        override fun onChanged() {
            reset()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            reset()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            reset()
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            reset()
        }

        private fun reset() {
            headerPositions.clear()
            adapter?.itemCount?.let {
                (0 until it)
                        .filter { adapter!!.isStickyHeader(it) }
                        .forEach { headerPositions.add(it) }

                // Remove sticky header immediately if the entry it represents has been removed. A layout will follow.
                if (stickyHeader != null && !headerPositions.contains(stickyHeaderPosition)) {
                    scrapStickyHeader(null)
                }
            }
        }
    }
}
