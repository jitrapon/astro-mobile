package io.jitrapon.glom.board.item

import androidx.recyclerview.widget.DiffUtil
import io.jitrapon.glom.base.util.isNullOrEmpty

/**
 * Calculates the difference between two board item lists, whose result can be used to notify
 * RecyclerView's adapter to update changes accordingly.
 *
 * @author Jitrapon Tiachunpun
 */
class BoardItemDiffCallback(private val oldItems: List<BoardItemUiModel>?, private val newItems: List<BoardItemUiModel>?) : DiffUtil.Callback() {

    override fun getOldListSize() = if (oldItems.isNullOrEmpty()) 0 else oldItems!!.size

    override fun getNewListSize() = if (newItems.isNullOrEmpty()) 0 else newItems!!.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return (oldItems?.getOrNull(oldItemPosition)?.itemType == newItems?.getOrNull(newItemPosition)?.itemType) &&
                (oldItems?.getOrNull(oldItemPosition)?.itemId == newItems?.getOrNull(newItemPosition)?.itemId)
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldItems?.getOrNull(oldItemPosition)?.equals(newItems?.getOrNull(newItemPosition)) ?: false
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        return oldItems?.getOrNull(oldItemPosition)?.getChangePayload(newItems?.getOrNull(newItemPosition))
    }
}