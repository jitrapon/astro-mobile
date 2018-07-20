package io.jitrapon.glom.board.event.widget

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
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
                           title: CharSequence? = null) :
        AlertDialog(context, 0), DialogInterface.OnClickListener {

    private lateinit var calendarView: GlomCalendarView

    //region constructors

    init {
        title?.let (::setTitle)
        setView(LayoutInflater.from(getContext()).inflate(R.layout.event_date_picker_dialog, null).apply {
            findViewById<GlomCalendarView>(R.id.calendar_view)?.let {
                calendarView = it
                it.setSelectionMode(selectionMode)
                it.select(Date().setTime(year, monthOfYear, dayOfMonth))
            }
        })
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
        }
    }

    //endregion

    val year: Int? get() = calendarView.year

    val month: Int? get() = calendarView.month

    val dayOfMonth: Int? get() = calendarView.day
}
