package io.jitrapon.glom.base.ui.widget.calendar
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import com.kizitonwose.calendarview.CalendarView
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import io.jitrapon.glom.base.util.*
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.ZoneId
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

    private var onDateSelectListener: ((date: Date, isSelected: Boolean) -> Unit)? = null

    private var selectedDates = mutableSetOf<LocalDate>()

    private val today = LocalDate.now()

    /**
     * All the selection mode of the calendar
     */
    enum class SelectionMode {
        SINGLE, MULTIPLE, RANGE
    }

    @SuppressLint("SetTextI18n")
    fun init(monthTextView: TextView, dayLegendView: ViewGroup, selectionMode: SelectionMode = SelectionMode.SINGLE,
             isEditable: Boolean = true, onDateSelectListener: ((Date, Boolean) -> Unit)? = null) {
        this.selectionMode = selectionMode
        this.editable = isEditable
        this.onDateSelectListener = onDateSelectListener

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

    }

    /**
     * Clears all date selection
     */
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

        dayWidth = (((parentWidth() - (monthPaddingStart + monthPaddingEnd)) / NUM_DAYS_IN_WEEK) + 0.5).toInt()
        dayHeight = COLLAPSED_STATE_HEIGHT_DP.px

        setup(firstMonth, lastMonth, firstDayOfWeek)
        scrollToMonth(currentMonth)
    }

    inner class DayViewContainer(view: View) : ViewContainer(view) {

        private val dateText: TextView = view.findViewById(io.jitrapon.glom.R.id.calendar_item_date_textview)
        private val selectIndicator: View = view.findViewById(io.jitrapon.glom.R.id.calendar_item_selected_indicator)
        private val leftSelectIndicator: View = view.findViewById(io.jitrapon.glom.R.id.calendar_item_selected_indicator_left_half)
        private val rightSelectIndicator: View = view.findViewById(io.jitrapon.glom.R.id.calendar_item_selected_indicator_right_half)

        lateinit var day: CalendarDay

        private val appearanceAnimationDelay = 100L

        init {
            view.setOnClickListener {
                if (editable) {
                    if (day.inThisMonth) {
                        if (day.isSelected) unselectDay(day, true)
                        else selectDay(day, true)
                    }
                }
            }
            leftSelectIndicator.hide()
            rightSelectIndicator.hide()
            selectIndicator.hide()
        }

        fun bindDay() {
            dateText.text = day.date.dayOfMonth.toString()

            if (day.isSelected && day.inThisMonth) {
                showSelected(day.inThisMonth)
            }
            else {
                hideSelected(day.inThisMonth)
            }
        }

        private fun showSelected(animate: Boolean = true) {
            val textColor = if (day.inThisMonth) context.attrColor(android.R.attr.textColorPrimaryInverse) else
                context.attrColor(android.R.attr.textColorSecondaryInverse)
            if (animate) {
                selectIndicator.show()
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
            }
            else {
                selectIndicator.show()
                dateText.setTextColor(textColor)
            }
        }

        private fun hideSelected(animate: Boolean = true) {
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
            }
            else {
                selectIndicator.hide()
                dateText.setTextColor(textColor)
            }
        }

        internal fun selectDay(day: CalendarDay, shouldInvokeCallback: Boolean) {
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

                        if (shouldInvokeCallback) {
                            onDateSelectListener?.invoke(day.toDate(), true)
                        }
                    }
                }
                SelectionMode.MULTIPLE -> {
                    selectedDates.add(day.date)
                    notifyDayChanged(day)

                    if (shouldInvokeCallback) {
                        onDateSelectListener?.invoke(day.toDate(), true)
                    }
                }
                SelectionMode.RANGE -> {

                }
            }
        }

        internal fun unselectDay(day: CalendarDay, shouldInvokeCallback: Boolean) {
            if (selectedDates.remove(day.date)) {
                when (selectionMode) {
                    SelectionMode.SINGLE, SelectionMode.MULTIPLE -> {
                        notifyDayChanged(day)

                        if (shouldInvokeCallback) {
                            onDateSelectListener?.invoke(day.toDate(), false)
                        }
                    }
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

    private fun CalendarDay.toDate(): Date = Date(date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000)

    //endregion
}