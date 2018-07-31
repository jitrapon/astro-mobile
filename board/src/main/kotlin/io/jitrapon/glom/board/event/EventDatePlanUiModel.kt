package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.UiModel

data class EventDatePlanUiModel(val datePolls: MutableList<EventDatePollUiModel>,
                                var itemsChangedIndices: List<Int>?,
                                var selectedDatePoll: EventDatePollUiModel?,
                                override var status: UiModel.Status) : UiModel
