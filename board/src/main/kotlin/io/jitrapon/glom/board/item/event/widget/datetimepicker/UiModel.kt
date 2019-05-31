package io.jitrapon.glom.board.item.event.widget.datetimepicker

import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.base.model.UiModel
import java.util.Date

data class DateChoiceUiModel(val dayOfWeek: AndroidString,
                             val dayOfMonth: AndroidString,
                             val date: Date,
                             override var status: UiModel.Status = UiModel.Status.NEGATIVE): UiModel

data class TimeChoiceUiModel(val timeOfDay: AndroidString,
                             val date: Date,
                             override var status: UiModel.Status = UiModel.Status.NEGATIVE): UiModel

data class ConfirmDateTimeEvent(val start: Date?,
                                val end: Date?,
                                val isFullDay: Boolean): UiActionModel
