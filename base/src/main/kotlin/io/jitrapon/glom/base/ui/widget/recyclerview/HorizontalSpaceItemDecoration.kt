package io.jitrapon.glom.base.ui.widget.recyclerview

import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.view.View

/**
 * A RecyclerView's ItemDecorator that simplifies controlling the spacing between
 * two items in a horizontal linear layout
 *
 * Created by Jitrapon
 */
class HorizontalSpaceItemDecoration(private val spacing: Int, private val centerFirstItem: Boolean = false) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position != parent.adapter.itemCount - 1) {
            outRect.right = spacing
        }
        if (position == 0 && centerFirstItem) {
            outRect.left = Math.round((parent.width / 2f) - (view.width / 2f))
        }
    }
}
