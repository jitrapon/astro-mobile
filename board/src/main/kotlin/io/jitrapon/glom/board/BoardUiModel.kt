package io.jitrapon.glom.board

import io.jitrapon.glom.base.data.UiModel
import io.jitrapon.glom.board.data.BoardItem

/**
 * Created by Jitrapon on 11/18/2017.
 */
class BoardUiModel(override val status: UiModel.Status = UiModel.Status.SUCCESS,
                   val items: List<BoardItem>? = null) : UiModel