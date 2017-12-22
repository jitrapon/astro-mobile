package io.jitrapon.glom.board

/**
 * @author Jitrapon Tiachunpun
 */
data class ErrorItemUiModel(override val itemType: Int = BoardItemUiModel.TYPE_ERROR, override val itemId: String? = null) : BoardItemUiModel {

    override fun getChangePayload(other: BoardItemUiModel?): List<Int> {
        return ArrayList()
    }
}