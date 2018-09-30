package io.jitrapon.glom.board.item.event.plan

import io.jitrapon.glom.base.model.UiModel

data class EventPlacePlanUiModel(val placePolls: MutableList<EventPlacePollUiModel>,
                                 var pollChangedIndices: MutableList<Int>?,
                                 val placeCards: MutableList<EventPlacePollUiModel>,
                                 var cardChangedIndices: MutableList<Int>?,
                                 override var status: UiModel.Status) : UiModel
