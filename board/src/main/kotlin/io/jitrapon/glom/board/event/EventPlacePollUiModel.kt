package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel

data class EventPlacePollUiModel(val id: String,
                                 var name: AndroidString?,
                                 var address: AndroidString?,
                                 var description: AndroidString?,
                                 var avatar: String?,
                                 var count: Int,
                                 var actionButton: ButtonUiModel?,
                                 override var status: UiModel.Status) : UiModel