package io.jitrapon.glom.board.item.event.widget

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.WindowManager
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.setTime
import java.util.*

/**
 * Custom dialog to display {@link io.jitrapon.glom.board.event.widget.GlomCalendarView}
 * that can show events and date range
 *
 * @author Jitrapon Tiachunpun
 */
class GlomDatePickerDialog(context: Context, private val listener: DatePickerDialog.OnDateSetListener?,
                           year: Int, monthOfYear: Int, dayOfMonth: Int,
                           selectionMode: GlomCalendarView.SelectionMode = GlomCalendarView.SelectionMode.SINGLE,
                           title: CharSequence? = null,
                           dimBehind: Boolean = true) :
        AlertDialog(context, 0), DialogInterface.OnClickListener {

    private lateinit var calendarView: GlomCalendarView

    private var onNeutralButtonClickListener: ((DialogInterface) -> Unit)? = null

    //region constructors

    init {
        if (!dimBehind) window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        title?.let (::setTitle)
        setView(LayoutInflater.from(getContext()).inflate(io.jitrapon.glom.board.R.layout.event_date_picker_dialog, null).apply {
            findViewById<GlomCalendarView>(io.jitrapon.glom.board.R.id.event_date_picker_calendar_view)?.let {
                calendarView = it
                it.setSelectionMode(selectionMode)
                it.select(Date().setTime(year, monthOfYear, dayOfMonth), true)
            }
            setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.done_button), this@GlomDatePickerDialog)
            setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel), this@GlomDatePickerDialog)
            setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.date_picker_set_time_button), this@GlomDatePickerDialog)
        })
    }

    fun setSelectionMode(selectionMode: GlomCalendarView.SelectionMode) {
        calendarView.setSelectionMode(selectionMode)
    }

    fun setMinDate(minDate: Date?) {
        minDate?.let {
            calendarView.setSelectableDateRange(it to null)
        }
    }

    fun setOnNeutralButtonClicked(listener: ((DialogInterface) -> Unit)) {
        onNeutralButtonClickListener = listener
    }

    //endregion
    //region internal callbacks

    override fun onClick(dialog: DialogInterface, which: Int) {
        when (which) {
            BUTTON_POSITIVE -> {
                listener?.let {
                    calendarView.clearFocus()
                    calendarView.let { calendar ->
                        if (calendar.hasSelected) {
                            it.onDateSet(null, calendar.year!!, calendar.month!!, calendar.day!!)
                        }
                    }
                }
            }
            BUTTON_NEGATIVE -> cancel()
            BUTTON_NEUTRAL -> onNeutralButtonClickListener?.invoke(dialog)
        }
    }

    //endregion

    val year: Int? get() = calendarView.year

    val month: Int? get() = calendarView.month

    val dayOfMonth: Int? get() = calendarView.day
}