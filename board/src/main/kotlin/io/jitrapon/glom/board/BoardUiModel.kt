package io.jitrapon.glom.board

import androidx.recyclerview.widget.DiffUtil
import io.jitrapon.glom.base.model.UiModel

/**
 * UiModel that controls visual representation of the board with items
 *
 * @author Jitrapon Tiachunpun
 */
data class BoardUiModel(override var status: UiModel.Status = UiModel.Status.SUCCESS,
                        var items: MutableList<BoardItemUiModel>? = null,
                        var diffResult: DiffUtil.DiffResult? = null,
                        var itemsChangedIndices: MutableList<Pair<Int, Any?>>? = null,
                        var requestPlaceInfoItemIds: List<String>? = null,
                        var saveItem: BoardItem? = null) : UiModel