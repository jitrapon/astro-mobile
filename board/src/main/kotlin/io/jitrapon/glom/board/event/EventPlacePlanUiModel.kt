package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.UiModel

data class EventPlacePlanUiModel(val placePolls: MutableList<EventPlacePollUiModel>,
                                 var itemsChangedIndices: MutableList<Int>?,
                                 override var status: UiModel.Status) : UiModel
