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
import io.jitrapon.glom.base.util.*
import org.threeten.bp.LocalDate
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

    private var selectedDates = mutableSetOf<LocalDate>()

    private val today = LocalDate.now()

    /**
     * All the selection mode of the calendar
     */
    enum class SelectionMode {
        NONE, SINGLE, MULTIPLE, RANGE
    }

    @SuppressLint("SetTextI18n")
    fun init(monthTextView: TextView, dayLegendView: ViewGroup) {
        dayBinder = object : DayBinder<DayViewContainer> {

            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, day: CalendarDay) {
                with(container) {
                    this.day = day
                    bindDay()
                }
            }
        }

        monthScrollListener = {
            monthTextView.text = "${it.yearMonth.month.name.toLowerCase().capitalize()} ${it.year}"
        }

        val daysInWeek = getShortWeekDays().takeLast(7)
        dayLegendView.children.forEachIndexed { index, view ->
            (view as? TextView)?.apply {
                text = daysInWeek[index].toUpperCase()
            }
        }

        viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    /**
     * Sets the date selection mode for the calendar
     */
    var selectionMode: SelectionMode = SelectionMode.SINGLE

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

    inner class DayViewContainer(view: View) : ViewContainer(view) {

        private val dateText: TextView = view.findViewById(R.id.calendar_item_date_textview)
        private val selectIndicator: View = view.findViewById(R.id.calendar_item_selected_indicator)

        lateinit var day: CalendarDay

        init {
            view.setOnClickListener {
                if (selectionMode != SelectionMode.NONE) {
                    if (day.inThisMonth) {
                        if (day.isSelected) unselectDay(day)
                        else selectDay(day)
                    }
                }
            }
        }

        fun bindDay() {
            dateText.text = day.date.dayOfMonth.toString()

            if (day.isSelected) {
                selectIndicator.show()
                dateText.setTextColor(
                        if (day.inThisMonth) context.attrColor(android.R.attr.textColorPrimaryInverse)
                        else context.attrColor(android.R.attr.textColorSecondaryInverse)
                )
            }
            else {
                selectIndicator.hide()
                dateText.setTextColor(
                        when {
                            day.date == today -> context.colorPrimary()
                            day.inThisMonth -> context.attrColor(android.R.attr.textColorPrimary)
                            else -> context.attrColor(android.R.attr.textColorSecondary)
                        }
                )
            }
        }

        internal fun selectDay(day: CalendarDay) {
            when (selectionMode) {
                SelectionMode.SINGLE -> {
                    selectedDates.apply {
                        val previouslySelectedDate = if (isNotEmpty()) first() else null
                        add(day.date)
                        notifyDayChanged(day)
                        previouslySelectedDate?.let {
                            remove(it)
                            notifyDateChanged(it)
                        }
                    }
                }
                else -> Unit
            }
        }

        internal fun unselectDay(day: CalendarDay) {
            if (selectedDates.remove(day.date)) {
                when (selectionMode) {
                    SelectionMode.SINGLE -> notifyDayChanged(day)
                    else -> Unit
                }
            }
        }
    }

    //region helpers

    private val CalendarDay.inThisMonth: Boolean
        get() = owner == DayOwner.THIS_MONTH

    private val CalendarDay.isSelected: Boolean
        get() = selectedDates.contains(date)

    //endregion
}