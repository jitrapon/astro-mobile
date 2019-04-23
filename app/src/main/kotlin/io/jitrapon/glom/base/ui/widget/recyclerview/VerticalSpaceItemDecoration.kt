package io.jitrapon.glom.base.ui.widget.recyclerview

import android.graphics.Rect
import android.view.View

/**
 * A RecyclerView's ItemDecorator that simplifies controlling the spacing between
 * two items in a horizontal linear layout
 *
 * Created by Jitrapon
 */
class VerticalSpaceItemDecoration(private val spacing: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) != parent.adapter!!.itemCount - 1) {
            outRect.bottom = spacing
        }
    }
}
