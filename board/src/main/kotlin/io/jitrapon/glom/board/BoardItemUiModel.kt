package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.UiModel

/**
 * @author Jitrapon Tiachunpun
 */
interface BoardItemUiModel : UiModel {

    val itemId: String?
    val itemType: Int

    companion object {

        const val TYPE_ERROR = -1
        const val TYPE_HEADER = 0
        const val TYPE_EVENT = 1
        const val TYPE_FILE = 2
        const val TYPE_DRAWING = 3
        const val TYPE_LINK = 4
        const val TYPE_LIST = 5
        const val TYPE_NOTE = 6
    }

    /**
     * Returns the list of Integer defining which field of the UiModel has changed.
     * Used by the RecyclerView adapter's onBindViewHolder. An empty list represents a full update
     * of all fields.
     */
    fun getChangePayload(other: BoardItemUiModel?): List<Int>
}