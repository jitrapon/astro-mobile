package io.jitrapon.glom.base.ui.widget

import android.app.DatePickerDialog
import android.content.Context
import android.widget.DatePicker
import io.jitrapon.glom.base.model.DateTimePickerUiModel
import io.jitrapon.glom.base.util.toDayMonthYear
import java.util.*

/**
 * Wrapper around an implementation of a DateTime picker for abstracting away the details of this widget
 * to the caller.
 *
 * Created by Jitrapon
 */
class DateTimePicker(context: Context): DatePickerDialog.OnDateSetListener {

    private val datePicker: DatePickerDialog

    private var onDateSetListener: ((Date) -> Unit)? = null

    init {
        val (day, month, year) = Date().toDayMonthYear()
        datePicker = DatePickerDialog(context, this, year, month, day)
    }

    fun show(uiModel: DateTimePickerUiModel) {
        datePicker.apply {
            show()
        }
    }

    fun setDateTimeCallback(callback: (Date) -> Unit) {
        this.onDateSetListener = callback
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int) {
        onDateSetListener?.invoke(Date())
    }
}