package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.UiModel

data class EventDatePlanUiModel(val datePolls: MutableList<EventDatePollUiModel>,
                                val pollStatusButton: ButtonUiModel,
                                override var status: UiModel.Status) : UiModel
