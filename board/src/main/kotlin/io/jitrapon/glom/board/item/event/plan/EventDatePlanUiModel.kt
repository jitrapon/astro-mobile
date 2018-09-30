package io.jitrapon.glom.board.item.event.plan

import io.jitrapon.glom.base.model.UiModel

data class EventDatePlanUiModel(val datePolls: MutableList<EventDatePollUiModel>,
                                var itemsChangedIndices: List<Int>?,
                                override var status: UiModel.Status) : UiModel
