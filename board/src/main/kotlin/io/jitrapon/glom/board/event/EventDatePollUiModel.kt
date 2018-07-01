package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel

data class EventDatePollUiModel(var date: AndroidString,
                                var time: AndroidString,
                                var count: Int,
                                override var status: UiModel.Status) : UiModel