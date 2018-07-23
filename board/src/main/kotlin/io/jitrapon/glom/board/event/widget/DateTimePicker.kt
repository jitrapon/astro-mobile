package io.jitrapon.glom.board.event.widget

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
    private lateinit var datePicker: GlomDatePickerDialog

    /* TimePicker dialog */
    private lateinit var timePicker: TimePickerDialog

    /* cached uimodel */
    private var picker: DateTimePickerUiModel? = null

    /* callback for when date is set by the DatePicker dialog */
    private var onDateSetListener: ((Date) -> Unit)? = null

    /* callback for when date is cancelled */
    private var onCancelListener: (() -> Unit)? = null

    /* current date set so far */
    private val calendar = Calendar.getInstance()

    /* whether or not the mode is range */
    private var isRangeMode: Boolean = false

    /* whether or not dialog is showing for end date */
    private var isSelectingEndDate: Boolean = false

    /* current end date set so far */
    private var endCalendar: Calendar? = null

    /* whether or not to invoke callback */
    private var shouldInvokeCallback: Boolean = true

    /* callback for when date is set by the DateRangePicker dialog */
    private var onDateRangeSetListener: ((Pair<Date, Date?>) -> Unit)? = null

    /**
     * Call this to display the date and time dialog
     */
    fun show(picker: DateTimePickerUiModel, onDateTimeSet: (Date) -> Unit, onCancel: () -> Unit) {
        onDateSetListener = onDateTimeSet
        onCancelListener = onCancel

        if (this.picker != picker) {
            val (day, month, year) = picker.defaultDate.toDayMonthYear()
            calendar.time = picker.defaultDate
            datePicker = GlomDatePickerDialog(context, this, year, month, day).apply {
                setOnNeutralButtonClicked {
                    it.dismiss()
                    onDateTimeSet(calendar.let {
                        it[Calendar.YEAR] = datePicker.year ?: 0
                        it[Calendar.MONTH] = datePicker.month ?: 0
                        it[Calendar.DAY_OF_MONTH] = datePicker.dayOfMonth ?: 0
                        it.time
                    })
                }
                create()
                setOnCancelListener {
                    onCancel()
                }
            }
            this.picker = picker
        }
        datePicker.apply {
            setMinDate(picker.minDate)
            show()
        }
    }

    /**
     * Call this to display the time, then optional end date and time dialog
     */
    fun showRangePicker(picker: DateTimePickerUiModel, onDateTimeSet: (Pair<Date, Date?>) -> Unit, onCancel: () -> Unit) {
        isRangeMode = true
        onDateRangeSetListener = onDateTimeSet
        onCancelListener = onCancel
        isSelectingEndDate = false
        endCalendar = null
        shouldInvokeCallback = true

        if (this.picker != picker) {
            calendar.time = picker.defaultDate

            timePicker = TimePickerDialog(context, this@DateTimePicker, calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE), DateFormat.is24HourFormat(context))
            timePicker.apply {
                setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(io.jitrapon.glom.board.R.string.event_plan_date_choose_poll_end_title)) { dialog, _ ->
                    // we have to trigger the BUTTON_POSITIVE action as there are no current public API to retrieve the internal
                    // timepicker
                    shouldInvokeCallback = false
                    timePicker.onClick(dialog, DialogInterface.BUTTON_POSITIVE)

                    showEndDatePicker(picker, onDateTimeSet, onCancel)
                }
                create()
                setOnCancelListener {
                    onCancelListener?.invoke()
                }
            }
        }
        timePicker.show()
    }

    private fun showEndDatePicker(picker: DateTimePickerUiModel, onDateTimeSet: (Pair<Date, Date?>) -> Unit, onCancel: () -> Unit) {
        isSelectingEndDate = true

        val (day, month, year) = calendar.time.toDayMonthYear()
        datePicker = GlomDatePickerDialog(context, this, year, month, day).apply {
            setMinDate(calendar.time)
            create()
            getButton(DialogInterface.BUTTON_NEUTRAL).text = context.getString(io.jitrapon.glom.board.R.string.event_plan_date_reset_poll_start_time)
            setOnNeutralButtonClicked {
                it.dismiss()
                showRangePicker(picker, onDateTimeSet, onCancel)
            }
            setOnCancelListener {
                onCancelListener?.invoke()
            }
            show()
        }
    }

    /**
     * Called when the DialogInterface.BUTTON_POSITIVE is clicked
     */
    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        if (isRangeMode) {
            if (!isSelectingEndDate) {
                // should not arrive here
            }
            else {
                endCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    // show the time picker
                    timePicker = TimePickerDialog(context, this@DateTimePicker, get(Calendar.HOUR_OF_DAY),
                            get(Calendar.MINUTE), DateFormat.is24HourFormat(context))
                    timePicker.apply {
                        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.pick_date_button)) { dialog, _ ->
                            dialog.dismiss()
                            datePicker.show()
                        }
                        create()
                        setOnCancelListener {
                            onCancelListener?.invoke()
                        }
                        show()
                    }
                }
            }
        }
        else {
            calendar.apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // show the time picker
                timePicker = TimePickerDialog(context, this@DateTimePicker, get(Calendar.HOUR_OF_DAY),
                        get(Calendar.MINUTE), DateFormat.is24HourFormat(context))
                timePicker.apply {
                    setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.pick_date_button)) { dialog, _ ->
                        dialog.dismiss()
                        datePicker.show()
                    }
                    create()
                    setOnCancelListener {
                        onCancelListener?.invoke()
                    }
                    show()
                }
            }
        }
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        if (isRangeMode) {
            if (!isSelectingEndDate) {
                calendar.apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                if (shouldInvokeCallback) {
                    onDateRangeSetListener?.invoke(calendar.time to null)
                }
            }
            else {
                onDateRangeSetListener?.invoke(calendar.time to endCalendar?.run {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    time
                })
            }
        }
        else {
            onDateSetListener?.invoke(calendar.run {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                time
            })
        }
    }
}