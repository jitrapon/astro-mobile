package io.jitrapon.glom.base.ui.widget.calendar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.core.view.isVisible
import com.kizitonwose.calendarview.CalendarView
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.*
import org.threeten.bp.*
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
class GlomCalendarView : LinearLayout, ViewTreeObserver.OnGlobalLayoutListener {

    constructor(context: Context) : super(context) {
        setupViews()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setupViews()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        setupViews()
    }

    private var onDateSelectListener: ((date: Date, isSelected: Boolean) -> Unit)? = null
    private var decoratorSources: ArrayList<DecoratorSource> = arrayListOf()

    private var selectedDates = mutableSetOf<LocalDate>()
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null

    private val today = LocalDate.now()
    private var currMonth: Month = Month.JANUARY

    private lateinit var monthTextView: TextView
    private lateinit var dayLegendView: LinearLayout
    private lateinit var calendarView: CalendarView
    private var initialSelections: Array<Date>? = null

    var isInitialized: Boolean = false
        private set

    /**
     * All the selection mode of the calendar
     */
    enum class SelectionMode {
        SINGLE, MULTIPLE, RANGE_START, RANGE_END
    }

    private fun setupViews() {
        inflate(context, R.layout.calendar_view, this)
        monthTextView = findViewById(R.id.calendar_view_month_textview)
        dayLegendView = findViewById(R.id.calendar_item_day_legend)
        calendarView = findViewById(R.id.calendar_recycler_view)

        setupDayBinder()

        setupMonthScrollListener()

        setupDayLegends()

        // update the calendar day size upon finishing laying out
        // see onGlobalLayout()
        viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    @SuppressLint("SetTextI18n")
    fun init(initialSelections: Array<Date>? = null, decoratorSources: ArrayList<DecoratorSource> = arrayListOf(),
             selectionMode: SelectionMode = SelectionMode.SINGLE, isEditable: Boolean = true,
             onDateSelectListener: ((Date, Boolean) -> Unit)? = null) {
        this.selectionMode = selectionMode
        this.decoratorSources = decoratorSources
        this.editable = isEditable
        this.onDateSelectListener = onDateSelectListener
        this.initialSelections = initialSelections
    }

    private fun setupDayBinder() {
        calendarView.dayBinder = object : DayBinder<DayViewContainer> {

            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, day: CalendarDay) {
                with(container) {
                    this.day = day
                    bindDay()
                }
            }
        }
    }

    private fun setupMonthScrollListener() {
        calendarView.monthScrollListener = {
            monthTextView.text = "${it.yearMonth.month.name.toLowerCase().capitalize()} ${it.year}"
            currMonth = it.yearMonth.month
        }
    }

    private fun setupDayLegends() {
        val daysInWeek = getShortWeekDays().takeLast(7)
        dayLegendView.children.forEachIndexed { index, view ->
            (view as? TextView)?.apply {
                text = daysInWeek[index].toUpperCase()
            }
        }
    }

    override fun onGlobalLayout() {
        viewTreeObserver.removeOnGlobalLayoutListener(this)

        calendarView.apply {
            dayWidth = (((parentWidth() - (monthPaddingStart + monthPaddingEnd)) / NUM_DAYS_IN_WEEK) + 0.5).toInt()
            dayHeight = COLLAPSED_STATE_HEIGHT_DP.px

            val currentMonth = YearMonth.now()
            val firstMonth = currentMonth.minusMonths(10)
            val lastMonth = currentMonth.plusMonths(10)
            val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            setup(firstMonth, lastMonth, firstDayOfWeek)
            scrollToMonth(currentMonth)

            val selections = initialSelections ?: return
            val initialSelectionMode = selectionMode
            if (selectionMode == SelectionMode.RANGE_START || selectionMode == SelectionMode.RANGE_END) {
                try {
                    selectionMode = SelectionMode.RANGE_START
                    val startDate = selections[0]
                    if (startDate.time != 0L) {
                        selectDay(startDate.toLocalDate(), false)

                        val endDate = selections[1]
                        if (endDate.time != 0L) {
                            selectionMode = SelectionMode.RANGE_END
                            selectDay(endDate.toLocalDate(), false)
                        }
                        selectionMode = initialSelectionMode
                        if (selectionMode == SelectionMode.RANGE_END) {
                            reloadDay(startDate.toLocalDate())
                        }
                    }
                    else {
                        val endDate = selections[1]
                        if (endDate.time != 0L) {
                            selectDay(endDate.toLocalDate(), false)
                        }
                    }
                } catch (ex: Exception) {
                    AppLogger.e(ex)
                    selectionMode = initialSelectionMode
                }
            }
            else {
                for (selection in selections) {
                    selectDay(selection.toLocalDate(), false)
                }
            }
        }

        isInitialized = true
    }

    /**
     * Sets the date selection mode for the calendar
     */
    var selectionMode: SelectionMode = SelectionMode.MULTIPLE

    /**
     * Whether or not this calendar's selections are editable
     */
    var editable: Boolean = true

    /**
     * Selects a date. If it's already selected, nothing happens.
     * Calling this will not trigger the onDateSelected callback.
     */
    fun select(date: Date, scrollToDate: Boolean, selected: Boolean = true) {
        if (selected) {
            selectDay(date.toLocalDate(), false)
        }
        else {
            unselectDay(date.toLocalDate(), false)
        }
    }

    /**
     * Clears all date selection
     */
    fun clear() {
        when (selectionMode) {
            SelectionMode.SINGLE, SelectionMode.RANGE_START -> {
                selectedDates.firstOrNull()?.let {
                    unselectDay(it, false)
                }
                startDate = null
            }
            SelectionMode.MULTIPLE -> {
                for (day in selectedDates) {
                    unselectDay(day, false)
                }
            }
            SelectionMode.RANGE_END -> {
                endDate = null
                calendarView.notifyCalendarChanged()
            }
        }
    }

    /**
     * Selects multiple dates starting from `start` date to `end` date.
     * If any dates in-between are already selected, their states will remain selected
     *
     * Calling this will not trigger the onDateSelected callback.
     */
    fun selectRange(start: Date, end: Date) {

    }

    private fun selectDay(date: LocalDate, shouldInvokeCallback: Boolean) {
        when (selectionMode) {
            SelectionMode.SINGLE -> {
                selectSingleMode(date, shouldInvokeCallback)
            }
            SelectionMode.MULTIPLE -> {
                selectedDates.add(date)
                reloadDay(date)

                if (shouldInvokeCallback) {
                    onDateSelectListener?.invoke(date.toDate(), true)
                }
            }
            SelectionMode.RANGE_START -> {
                startDate = date

                // if no end date is selected, treat this as just a single selection
                selectSingleMode(date, shouldInvokeCallback)
                if (endDate != null) {
                    calendarView.notifyCalendarChanged()
                }
            }
            SelectionMode.RANGE_END -> {
                endDate = date

                if (shouldInvokeCallback) {
                    onDateSelectListener?.invoke(date.toDate(), true)
                }

                calendarView.notifyCalendarChanged()
            }
        }
    }

    private fun selectSingleMode(date: LocalDate, shouldInvokeCallback: Boolean) {
        selectedDates.apply {
            val previouslySelectedDate = if (isNotEmpty()) first() else null
            add(date)
            reloadDay(date)

            previouslySelectedDate?.let {
                remove(it)
                reloadDay(it)
            }

            if (shouldInvokeCallback) {
                onDateSelectListener?.invoke(date.toDate(), true)
            }
        }
    }

    private fun unselectDay(date: LocalDate, shouldInvokeCallback: Boolean) {
        if (selectionMode == SelectionMode.RANGE_END) {
            endDate = null

            if (shouldInvokeCallback) {
                onDateSelectListener?.invoke(date.toDate(), false)
            }

            calendarView.notifyCalendarChanged()
        } else if (selectedDates.remove(date)) {
            when (selectionMode) {
                SelectionMode.SINGLE, SelectionMode.MULTIPLE -> {
                    reloadDay(date)

                    if (shouldInvokeCallback) {
                        onDateSelectListener?.invoke(date.toDate(), false)
                    }
                }
                SelectionMode.RANGE_START -> {
                    startDate = null
                    if (endDate == null) {
                        reloadDay(date)
                    } else {
                        endDate = null
                        calendarView.notifyCalendarChanged()
                    }

                    if (shouldInvokeCallback) {
                        onDateSelectListener?.invoke(date.toDate(), false)
                    }
                }
                SelectionMode.RANGE_END -> {
                    endDate = null

                    if (shouldInvokeCallback) {
                        onDateSelectListener?.invoke(date.toDate(), false)
                    }

                    calendarView.notifyCalendarChanged()
                }
            }
        }
    }

    inner class DayViewContainer(view: View) : ViewContainer(view) {

        private val dateText: TextView = view.findViewById(R.id.calendar_item_date_textview)
        private val selectIndicator: View = view.findViewById(R.id.calendar_item_selected_indicator)
        private val leftSelectIndicator: View = view.findViewById(R.id.calendar_item_selected_indicator_left_half)
        private val rightSelectIndicator: View = view.findViewById(R.id.calendar_item_selected_indicator_right_half)
        private val outlineIndicator: View = view.findViewById(R.id.calendar_item_selected_outline_indicator)
        val dot: ImageView = view.findViewById(R.id.calendar_item_dot)

        lateinit var day: CalendarDay

        private val appearanceAnimationDelay = 100L

        init {
            view.setOnClickListener {
                if (editable) {
                    if (day.inThisMonth && currMonth == day.date.month) {
                        if (day.isSelected) unselectDay(day.date, true)
                        else selectDay(day.date, true)
                    }
                }
            }
            leftSelectIndicator.hide()
            rightSelectIndicator.hide()
            selectIndicator.hide()
            outlineIndicator.hide()
            dot.hide()

            for (decorator in decoratorSources) {
                decorator.addView(view)
            }
        }

        fun bindDay() {
            dateText.text = day.date.dayOfMonth.toString()
            val shouldAnimateIndicator = day.inThisMonth && currMonth == day.date.month && isNotInRangeMode
            if (day.isSelected) {
                showIndicator(shouldAnimateIndicator)
            } else {
                hideIndicator(shouldAnimateIndicator)
            }

            if ((selectionMode == SelectionMode.RANGE_START || selectionMode == SelectionMode.RANGE_END)) {
                if (startDate != null && endDate != null && day.date.inBetween(startDate!!, endDate!!)) {
                    if (day.date.isBefore(endDate)) {
                        rightSelectIndicator.show()
                    }
                    if (day.date.isAfter(startDate)) {
                        leftSelectIndicator.show()
                    }
                    if (day.date.isEqual(endDate)) {
                        showIndicator(false)
                        rightSelectIndicator.hide()
                    }
                    if (day.date.isEqual(startDate)) {
                        showIndicator(false)
                        leftSelectIndicator.hide()
                    }
                    if (leftSelectIndicator.isVisible && rightSelectIndicator.isVisible) {
                        dateText.setTextColor(context.attrColor(android.R.attr.textColorPrimaryInverse))
                    }
                } else {
                    leftSelectIndicator.hide()
                    rightSelectIndicator.hide()
                    if (selectionMode == SelectionMode.RANGE_END) {
                        if (startDate != null && day.date == startDate!! && endDate == null) {
                            outlineIndicator.show()
                        }
                    }
                }
            } else {
                leftSelectIndicator.hide()
                rightSelectIndicator.hide()
            }

            for (decorator in decoratorSources) {
                decorator.decorate(day.date.toDate(), this)
            }
        }

        private fun showIndicator(animate: Boolean = true) {
            val textColor = if (day.inThisMonth) context.attrColor(android.R.attr.textColorPrimaryInverse) else
                context.attrColor(android.R.attr.textColorSecondaryInverse)
            if (animate) {
                selectIndicator.apply {
                    show()
                    scaleX = 0f
                    scaleY = 0f
                }
                val animX = ObjectAnimator.ofFloat(selectIndicator, "scaleX", 1.0f)
                val animY = ObjectAnimator.ofFloat(selectIndicator, "scaleY", 1.0f)
                AnimatorSet().apply {
                    duration = appearanceAnimationDelay
                    playTogether(animX, animY)
                    doOnEnd {
                        dateText.setTextColor(textColor)
                    }
                    start()
                }
            } else {
                selectIndicator.apply {
                    show()
                    scaleX = 1f
                    scaleY = 1f
                }
                dateText.setTextColor(textColor)
            }
        }

        private fun hideIndicator(animate: Boolean = true) {
            val textColor = when {
                day.date == today -> context.colorPrimary()
                day.inThisMonth -> context.attrColor(android.R.attr.textColorPrimary)
                else -> context.attrColor(android.R.attr.textColorSecondary)
            }
            if (animate) {
                val animX = ObjectAnimator.ofFloat(selectIndicator, "scaleX", 0.0f)
                val animY = ObjectAnimator.ofFloat(selectIndicator, "scaleY", 0.0f)
                AnimatorSet().apply {
                    duration = appearanceAnimationDelay
                    playTogether(animX, animY)
                    doOnEnd {
                        selectIndicator.hide()
                        dateText.setTextColor(textColor)
                    }
                    start()
                }
            } else {
                selectIndicator.hide()
                dateText.setTextColor(textColor)
            }
        }
    }

    private fun reloadDay(date: LocalDate) {
        calendarView.notifyDateChanged(date, DayOwner.PREVIOUS_MONTH)
        calendarView.notifyDateChanged(date, DayOwner.THIS_MONTH)
        calendarView.notifyDateChanged(date, DayOwner.NEXT_MONTH)
    }

    //region helpers

    private fun LocalDate.inBetween(startDate: LocalDate, endDate: LocalDate): Boolean {
        return isAfter(startDate.minusDays(1)) && isBefore(endDate.plusDays(1))
    }

    private val isNotInRangeMode: Boolean
        get() = (selectionMode == SelectionMode.RANGE_START && endDate == null) ||
                selectionMode == SelectionMode.SINGLE ||
                selectionMode == SelectionMode.MULTIPLE

    private val CalendarDay.inThisMonth: Boolean
        get() = owner == DayOwner.THIS_MONTH

    private val CalendarDay.isSelected: Boolean
        get() = if (selectionMode == SelectionMode.RANGE_END) {
            endDate == date
        } else {
            selectedDates.contains(date)
        }

    private fun LocalDate.toDate(): Date = Date(atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000)

    private fun Date.toLocalDate(): LocalDate = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()

    //endregion
}
