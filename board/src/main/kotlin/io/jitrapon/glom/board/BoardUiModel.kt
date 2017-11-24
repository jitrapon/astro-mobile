package io.jitrapon.glom.board

import io.jitrapon.glom.base.data.UiModel

/**
 * Created by Jitrapon on 11/18/2017.
 */
data class BoardUiModel(override val status: UiModel.Status = UiModel.Status.SUCCESS,
                   val items: List<BoardItemUiModel>? = null) : UiModel