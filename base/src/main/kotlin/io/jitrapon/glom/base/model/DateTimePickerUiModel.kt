package io.jitrapon.glom.base.model

import java.util.*

/**
 * UiModel class that represents the DateTimePicker
 *
 * Created by Jitrapon
 */
data class DateTimePickerUiModel(val defaultDate: Date,
                                 override var status: UiModel.Status = UiModel.Status.SUCCESS) : UiModel