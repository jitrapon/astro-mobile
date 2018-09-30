package io.jitrapon.glom.board.item.event.plan

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel

data class EventPlacePollUiModel(val id: String = "",
                                 var name: AndroidString? = null,
                                 var address: AndroidString? = null,
                                 var description: AndroidString? = null,
                                 var avatar: String? = null,
                                 var count: Int = 0,
                                 var actionButton: ButtonUiModel = ButtonUiModel(null),
                                 val isAddButton: Boolean = false,
                                 override var status: UiModel.Status) : UiModel