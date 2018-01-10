package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.UiModel

/**
 * @author Jitrapon Tiachunpun
 */
data class ErrorItemUiModel(override val itemType: Int = BoardItemUiModel.TYPE_ERROR,
                            override val itemId: String? = null,
                            override val status: UiModel.Status = UiModel.Status.SUCCESS) : BoardItemUiModel {

    override fun getChangePayload(other: BoardItemUiModel?): List<Int> {
        return ArrayList()
    }
}