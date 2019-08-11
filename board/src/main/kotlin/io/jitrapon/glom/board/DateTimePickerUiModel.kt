package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.board.item.event.EventItem
import java.util.*

/**
 * UiModel class that represents the DateTimePicker
 *
 * Created by Jitrapon
 */
data class DateTimePickerUiModel(val startDate: Date?,
                                 val endDate: Date?,
                                 val isStartDate: Boolean,
                                 val occupiedDates: HashMap<Date, List<EventItem>>?,
                                 val minDate: Date? = null,
                                 val isFullDay: Boolean = false,
                                 override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel
