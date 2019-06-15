package io.jitrapon.glom.base.ui.widget.calendar
import android.view.View
import android.widget.TextView
import com.kizitonwose.calendarview.ui.ViewContainer
import io.jitrapon.glom.R
import java.util.*

/**
 * A wrapper around third-party implementation of the CalendarView to avoid
 * having to call library-specific functions
 *
 * Created by Jitrapon
 */
class GlomCalendarView {

//    constructor(context: Context): super(context)
//
//    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

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
        get() = false

    /**
     * The year of the currently selected date
     */
    val year: Int?
        get() = null

    /**
     * The month of the currently selected date
     */
    val month: Int?
        get() = null

    /**
     * The day of the currently selected date
     */
    val day: Int?
        get() = null

    /**
     * Sets the date selection mode for the calendar
     */
    fun setSelectionMode(mode: SelectionMode) {

    }

    /**
     * Enables or disables date range to be selectable on the calendar
     */
    fun setSelectableDateRange(range: Pair<Date?, Date?>) {

    }

    /**
     * Called when a date is selected or unselected
     */
    fun onDateSelected(func: (Date, Boolean) -> Unit) {

    }

    /**
     * Selects a date. If it's already selected, nothing happens.
     * Calling this will not trigger the onDateSelected callback.
     */
    fun select(date: Date, scrollToDate: Boolean, selected: Boolean = true) {

    }

    fun clear() {

    }

    /**
     * Selects multiple dates starting from `start` date to `end` date.
     * If any dates in-between are already selected, their states will remain selected
     *
     * Calling this will not trigger the onDateSelected callback.
     */
    fun selectRange(start: Date, end: Date) {

    }
}

class DayViewContainer(view: View) : ViewContainer(view) {

    val textView = view.findViewById<TextView>(R.id.calendarDayText)
}