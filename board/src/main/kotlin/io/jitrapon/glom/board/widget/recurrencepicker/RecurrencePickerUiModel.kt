package io.jitrapon.glom.board.widget.recurrencepicker

import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.board.item.event.EventItem

data class RecurrencePickerUiModel(
    val event: EventItem,
    override var status: UiModel.Status = UiModel.Status.SUCCESS
) : UiModel
