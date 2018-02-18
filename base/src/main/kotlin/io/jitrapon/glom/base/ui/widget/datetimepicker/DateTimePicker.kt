package io.jitrapon.glom.base.ui.widget.datetimepicker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.text.format.DateFormat
import android.widget.DatePicker
import android.widget.TimePicker
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.DateTimePickerUiModel
import io.jitrapon.glom.base.util.toDayMonthYear
import java.util.*

/**
 * Wrapper around an implementation of a DateTime picker for abstracting away the details of this widget
 * to the caller.
 *
 * Created by Jitrapon
 */
class DateTimePicker(private val context: Context): DatePickerDialog.OnDateSetListener,
        TimePickerDialog.OnTimeSetListener {

    /* DatePicker dialog */
    private lateinit var datePicker: DatePickerDialog

    /* TimePicker dialog */
    private lateinit var timePicker: TimePickerDialog

    /* cached uimodel */
    private var picker: DateTimePickerUiModel? = null

    /* callback for when date is set by the DatePicker dialoog */
    private var onDateSetListener: ((Date) -> Unit)? = null

    /* callback for when date is cancelled */
    private var onCancelListener: (() -> Unit)? = null

    /* current date set so far */
    private val calendar = Calendar.getInstance()

    /**
     * Call this to display the dialog
     */
    fun show(picker: DateTimePickerUiModel, onDateTimeSet: (Date) -> Unit, onCancel: () -> Unit) {
        onDateSetListener = onDateTimeSet
        onCancelListener = onCancel

        if (this.picker != picker) {
            val (day, month, year) = picker.defaultDate.toDayMonthYear()
            calendar.time = picker.defaultDate
            datePicker = DatePickerDialog(context, this, year, month, day)
            datePicker.apply {
                setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.done_button), { dialog, _ ->
                    dialog.dismiss()
                    onDateTimeSet(calendar.let {
                        it[Calendar.YEAR] = datePicker.year
                        it[Calendar.MONTH] = datePicker.month
                        it[Calendar.DAY_OF_MONTH] = datePicker.dayOfMonth
                        it.time
                    })
                })
                create()
                getButton(DatePickerDialog.BUTTON_POSITIVE).text = context.getString(R.string.date_picker_set_time_button)
                setOnCancelListener {
                    onCancel()
                }
            }
            this.picker = picker
        }
        datePicker.show()
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int) {
        calendar.apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)

            // show the time picker
            timePicker = TimePickerDialog(context, this@DateTimePicker, get(Calendar.HOUR_OF_DAY),
                    get(Calendar.MINUTE), DateFormat.is24HourFormat(context))
            timePicker.apply {
                setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.pick_date_button), { dialog, _ ->
                    dialog.dismiss()
                    datePicker.show()
                })
                create()
                setOnCancelListener {
                    onCancelListener?.invoke()
                }
                show()
            }
        }
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        onDateSetListener?.invoke(calendar.run {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            time
        })
    }
}