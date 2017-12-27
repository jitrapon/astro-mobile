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
class HorizontalSpaceItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) != parent.adapter.itemCount - 1) {
            outRect.right = spacing
        }
    }
}
