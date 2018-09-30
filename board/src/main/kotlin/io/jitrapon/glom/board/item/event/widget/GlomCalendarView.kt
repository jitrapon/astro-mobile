package io.jitrapon.glom.board.item.event.widget

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

    private var listener: ((Date, Boolean) -> Unit)? = null

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
        get() = selectedDate != null

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
    fun onDateSelected(func: (Date, Boolean) -> Unit) {
        listener = func
        setOnDateChangedListener { _, date, selected ->
            listener?.invoke(date.date, selected)
        }
    }

    /**
     * Selects a date. If it's already selected, nothing happens.
     * Calling this will not trigger the onDateSelected callback.
     */
    fun select(date: Date, scrollToDate: Boolean, selected: Boolean = true) {
        setOnDateChangedListener(null)

        setDateSelected(date, selected)
        if (scrollToDate) {
            setCurrentDate(date)
        }

        setOnDateChangedListener{ _, d, isSelected ->
            listener?.invoke(d.date, isSelected)
        }
    }

    fun clear() {
        clearSelection()
    }

    /**
     * Selects multiple dates starting from `start` date to `end` date.
     * If any dates in-between are already selected, their states will remain selected
     *
     * Calling this will not trigger the onDateSelected callback.
     */
    fun selectRange(start: Date, end: Date) {
        setOnDateChangedListener(null)

        val counter = Calendar.getInstance().apply { time = start }
        val finish = Calendar.getInstance().apply { time = end }
        while (counter.before(finish) || counter == finish) {
            setDateSelected(counter, true)
            counter.add(Calendar.DATE, 1)
        }

        setOnDateChangedListener{ _, d, selected ->
            listener?.invoke(d.date, selected)
        }
    }
}