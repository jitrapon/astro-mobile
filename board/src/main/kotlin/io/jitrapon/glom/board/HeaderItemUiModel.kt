package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.AndroidString

/**
 * @author Jitrapon Tiachunpun
 */
data class HeaderItemUiModel(val text: AndroidString,
                        override val itemType: Int = BoardItemUiModel.TYPE_HEADER,
                        override val itemId: String? = null) : BoardItemUiModel {

    override fun getChangePayload(other: BoardItemUiModel?): List<Int> {
        return ArrayList()
    }
}