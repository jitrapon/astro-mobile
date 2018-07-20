package io.jitrapon.glom.board.event.widget

import android.content.Context
import android.util.AttributeSet
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import java.util.*

/**
 * A wrapper around third-party implementation of the CalendarView to avoid
 * having to call library-specific functions
 *
 * Created by Jitrapon
 */
class GlomCalendarView : MaterialCalendarView {

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    /**
     * All the selection mode of the calendar
     */
    enum class SelectionMode {
        NONE, SINGLE, MULTIPLE, RANGE
    }

    /**
     * Whether or not user has selected any dates
     */
    val hasSelected: Boolean
        get() = selectedDate == null

    /**
     * The year of the currently selected date
     */
    val year: Int?
        get() = selectedDate?.year

    /**
     * The month of the currently selected date
     */
    val month: Int?
        get() = selectedDate?.month

    /**
     * The day of the currently selected date
     */
    val day: Int?
        get() = selectedDate?.day

    /**
     * Sets the date selection mode for the calendar
     */
    fun setSelectionMode(mode: SelectionMode) {
        selectionMode = when (mode) {
            SelectionMode.NONE -> MaterialCalendarView.SELECTION_MODE_NONE
            SelectionMode.SINGLE -> MaterialCalendarView.SELECTION_MODE_SINGLE
            SelectionMode.MULTIPLE -> MaterialCalendarView.SELECTION_MODE_MULTIPLE
            SelectionMode.RANGE -> MaterialCalendarView.SELECTION_MODE_RANGE
        }
    }

    /**
     * Enables or disables date range to be selectable on the calendar
     */
    fun setSelectableDateRange(range: Pair<Date?, Date?>) {
        range.let { (minDate, maxDate) ->
            state().edit()
                    .setMinimumDate(minDate)
                    .setMaximumDate(maxDate)
                    .commit()
        }
    }

    /**
     * Called when a date is selected or unselected
     */
    inline fun onDateSelected(crossinline func: (Date, Boolean) -> Unit) {
        setOnDateChangedListener { _, date, selected ->
            func(date.date, selected)
        }
    }

    /**
     * Selects a date. If it's already selected, nothing happens
     */
    fun select(date: Date) {
        setDateSelected(date, true)
    }

    /**
     * Selects multiple dates starting from `start` date to `end` date.
     * If any dates in-between are already selected, their states will remain selected
     */
    fun selectRange(start: Date, end: Date) {
        val counter = Calendar.getInstance().apply { time = start }
        val finish = Calendar.getInstance().apply { time = end }
        while (counter.before(finish) || counter == finish) {
            setDateSelected(counter, true)
            counter.add(Calendar.DATE, 1)
        }
    }
}
