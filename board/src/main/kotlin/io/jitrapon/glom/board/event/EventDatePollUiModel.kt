package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel
import java.util.*

data class EventDatePollUiModel(val id: String,
                                var date: AndroidString,
                                var time: AndroidString,
                                var calendarStartDate: Date,
                                var calendarEndDate: Date?,
                                var count: Int,
                                override var status: UiModel.Status) : UiModel