package io.jitrapon.glom.base.ui.widget.calendar
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.view.children
import com.kizitonwose.calendarview.CalendarView
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.attrColor
import io.jitrapon.glom.base.util.getShortWeekDays
import io.jitrapon.glom.base.util.parentWidth
import io.jitrapon.glom.base.util.px
import org.threeten.bp.YearMonth
import org.threeten.bp.temporal.WeekFields
import java.util.*

const val NUM_DAYS_IN_WEEK = 7f
const val COLLAPSED_STATE_HEIGHT_DP = 48

/**
 * A wrapper around third-party implementation of the CalendarView to avoid
 * having to call library-specific functions
 *
 * Created by Jitrapon
 */

class GlomCalendarView : CalendarView, ViewTreeObserver.OnGlobalLayoutListener {

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    private var onDateSetListener: ((Date, Boolean) -> Unit)? = null

    /**
     * All the selection mode of the calendar
     */
    enum class SelectionMode {
        NONE, SINGLE, MULTIPLE, RANGE
    }

    @SuppressLint("SetTextI18n")
    fun init(monthTextView: TextView, dayLegendView: ViewGroup) {
        dayBinder = object : DayBinder<DayViewContainer> {
            // Called only when a new container is needed.
            override fun create(view: View) = DayViewContainer(view)

            // Called every time we need to reuse a container.
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.date.text = day.date.dayOfMonth.toString()
                if (day.owner == DayOwner.THIS_MONTH) {
                    container.date.setTextColor(context.attrColor(android.R.attr.textColorPrimary))
                }
                else {
                    container.date.setTextColor(context.attrColor(android.R.attr.textColorSecondary))
                }
            }
        }

        monthScrollListener = {
            monthTextView.text = "${it.yearMonth.month.name.toLowerCase().capitalize()} ${it.year}"
        }

        val daysInWeek = getShortWeekDays().takeLast(7)
        dayLegendView.children.forEachIndexed { index, view ->
            (view as TextView).apply {
                text = daysInWeek[index].toUpperCase()
            }
        }

        viewTreeObserver.addOnGlobalLayoutListener(this)
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
     * Called when a date is selected or unselected,
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

    override fun onGlobalLayout() {
        viewTreeObserver.removeOnGlobalLayoutListener(this)

        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(10)
        val lastMonth = currentMonth.plusMonths(10)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        setup(firstMonth, lastMonth, firstDayOfWeek)
        scrollToMonth(currentMonth)

        dayWidth = (((parentWidth() - (monthPaddingStart + monthPaddingEnd)) / NUM_DAYS_IN_WEEK) + 0.5).toInt()
        dayHeight = COLLAPSED_STATE_HEIGHT_DP.px
    }
}

class DayViewContainer(view: View) : ViewContainer(view) {

    val date = view.findViewById<TextView>(R.id.calendar_item_date_textview)
}