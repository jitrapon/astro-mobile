package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.base.model.UiModel

/**
 * @author Jitrapon Tiachunpun
 */
data class UserUiModel(val avatar: String?,
                       val username: String?,
                       override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel