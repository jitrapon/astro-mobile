package io.jitrapon.glom.base.ui.widget.recyclerview

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper

/**
 * Created by Jitrapon
 */
class ItemTouchHelperCallback(private val listener: OnItemChangedListener, private val longPressDragEnabled: Boolean = true,
                              private val swipeEnabled: Boolean = true) : ItemTouchHelper.Callback() {

    /**
     * To be implemented by a Recyclerview's adapter
     */
    interface OnItemChangedListener {

        fun onMove(fromPosition: Int, toPosition: Int)

        fun onSwipe(position: Int, isStartDir: Boolean)
    }

    override fun isLongPressDragEnabled(): Boolean = longPressDragEnabled

    override fun isItemViewSwipeEnabled(): Boolean = swipeEnabled

    override fun getMovementFlags(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int {
        return makeMovementFlags(ItemTouchHelper.UP.or(ItemTouchHelper.DOWN), ItemTouchHelper.START.or(ItemTouchHelper.END))
    }

    override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
        listener.onMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
        listener.onSwipe(viewHolder.adapterPosition, direction == ItemTouchHelper.START)
    }
}