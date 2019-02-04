package io.jitrapon.glom.board.item.event.preference

import androidx.recyclerview.widget.DiffUtil
import io.jitrapon.glom.base.model.PreferenceItemUiModel
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.board.item.BoardItemUiModel

/**
 * Calculates the difference between two preference item UiModel lists, whose result can be used to notify
 * RecyclerView's adapter to update changes accordingly.
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemPreferenceDiffCallback(private val oldItems: List<PreferenceItemUiModel>?, private val newItems: List<PreferenceItemUiModel>?) : DiffUtil.Callback() {

    override fun getOldListSize() = if (oldItems.isNullOrEmpty()) 0 else oldItems!!.size

    override fun getNewListSize() = if (newItems.isNullOrEmpty()) 0 else newItems!!.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return (oldItems?.getOrNull(oldItemPosition)?.headerTag == newItems?.getOrNull(newItemPosition)?.headerTag) &&
                (oldItems?.getOrNull(oldItemPosition)?.tag == newItems?.getOrNull(newItemPosition)?.tag)
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldItems?.getOrNull(oldItemPosition)?.equals(newItems?.getOrNull(newItemPosition)) ?: false
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? = null
}
