package io.jitrapon.glom.board.item.event.widget.datetimepicker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.DateTimePickerUiModel
import io.jitrapon.glom.base.model.LiveEvent
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.Date
import kotlin.collections.ArrayList

const val SIMPLE_VIEW_NUM_DATE_CHOICES = 5
const val NOT_SELECTED_INDEX = -1

/**
 * ViewModel class responsible for the bottom sheet date time picker
 */
class DateTimePickerViewModel : BaseViewModel() {

    private val observableDate = MutableLiveData<AndroidString>()

    private val observableTime = MutableLiveData<AndroidString>()

    private val observableFullDay = MutableLiveData<Boolean>()

    private val observableDateChoices = MutableLiveData<Array<DateChoiceUiModel>>()

    private val observableDayTimeChoice = MutableLiveData<Int>()

    private val observableTimeChoices = MutableLiveData<ArrayList<TimeChoiceUiModel>>()

    private val observableSelectedTimeChoice = MutableLiveData<TimeChoiceUiModel>()

    private val observableFinishEvent = LiveEvent<ConfirmDateTimeEvent>()

    private val observableTimePicker = LiveEvent<Date>()

    private var isStartDate: Boolean = false
    private var startDate: Date = Date()
    private val endDate: Date = Date()
    private val firstDate: Date = Date()
    private var minDate: Date? = null

    private val MORNING_CHOICE = 0
    private val AFTERNOON_CHOICE = 1
    private val EVENING_CHOICE = 2
    private val NIGHT_CHOICE = 3
    private val DEFAULT_MORNING_HOUR = 8
    private val DEFAULT_AFTERNOON_HOUR = 14
    private val DEFAULT_EVENING_HOUR = 18
    private val DEFAULT_NIGHT_HOUR = 22
    private val DEFAULT_MORNING_HOUR_RANGE = 6..11
    private val DEFAULT_AFTERNOON_HOUR_RANGE = 12..16
    private val DEFAULT_EVENING_HOUR_RANGE = 17..20
    private val DEFAULT_NIGHT_HOUR_RANGE = 21..23

    fun setDateTime(uiModel: DateTimePickerUiModel?) {
        uiModel ?: return
        isStartDate = uiModel.showStartDateFirst
        startDate.time = uiModel.startDate?.time ?: 0L  // time 0 ms from epoch means that this date is NULL
        endDate.time = uiModel.endDate?.time ?: 0L      // time 0 ms from epoch means that this date is NULL
        minDate = uiModel.minDate
        observableFullDay.value = uiModel.isFullDay
        resetDateTime()
    }

    private fun resetStartDateIfNeeded() {
        // we should set the start time accordingly to one hour prior to the new end time
        // if it is less than the start time already set, or if the start time has not been set
        if (endDate.time != 0L && (startDate.time == 0L || startDate.time > endDate.time)) {
            startDate = endDate.addHour(-1)
        }
    }

    private fun resetEndDateIfNeeded() {
        if (startDate.time > endDate.time) {
            endDate.time = 0L
        }
    }

    private fun resetDateTime() {
        val currDate = if (isStartDate) startDate else endDate
        val selectedDateChoiceIndex: Int
        val autoSelectTimeChoice: Boolean
        if (currDate.time == 0L) {
            firstDate.time = if (isStartDate || startDate.time == 0L) Date().roundToNextHour().time else
                startDate.addHour(1).time
            selectedDateChoiceIndex = NOT_SELECTED_INDEX
            autoSelectTimeChoice = false
        } else {
            firstDate.time = currDate.time
            selectedDateChoiceIndex = 0
            autoSelectTimeChoice = true
        }

        observableDate.value = getDate(currDate)
        observableTime.value = getTime(currDate)
        observableDateChoices.value = getDateChoices(firstDate, selectedDateChoiceIndex)
        val (dayTimeChoices, timeChoices) = getDayTimeChoices(firstDate, autoSelectTimeChoice)
        observableDayTimeChoice.value = dayTimeChoices
        observableTimeChoices.value = timeChoices
    }

    fun selectDateChoice(position: Int) {
        if (position < 0 || position > SIMPLE_VIEW_NUM_DATE_CHOICES - 1) return

        observableDateChoices.value = getDateChoices(firstDate, position)
        observableDateChoices.value?.get(position)?.date?.let {
            val (day, month, year) = it.toDayMonthYear()
            if (isStartDate) {
                startDate.setTime(year, month, day)
                observableDate.value = getDate(startDate)
                if (observableDayTimeChoice.value == NOT_SELECTED_INDEX) {
                    selectDayTimeChoice(MORNING_CHOICE)
                }
                resetEndDateIfNeeded()
            } 
            else {
                endDate.setTime(year, month, day)
                observableDate.value = getDate(endDate)
                if (observableDayTimeChoice.value == NOT_SELECTED_INDEX) {
                    if (startDate.time == 0L) {
                        selectDayTimeChoice(MORNING_CHOICE)
                    }
                    else {
                        endDate.time = startDate.addHour(1).time
                        val (dayTimeChoices, timeChoices) = getDayTimeChoices(endDate)
                        observableDayTimeChoice.value = dayTimeChoices
                        observableTimeChoices.value = timeChoices
                        observableTime.value = getTime(endDate)
                    }
                }
                resetStartDateIfNeeded()
            }
        }
    }

    fun selectCalendarDate(date: Date) {
        val (day, month, year) = date.toDayMonthYear()
        if (isStartDate) {
            startDate.time = startDate.setTime(year, month, day).time
            firstDate.time = startDate.time
            observableDate.value = getDate(startDate)

            resetEndDateIfNeeded()
        }
        else {
            endDate.time = endDate.setTime(year, month, day).time
            firstDate.time = endDate.time
            observableDate.value = getDate(endDate)

            resetStartDateIfNeeded()
        }
    }

    fun setTime(hourOfDay: Int, minute: Int) {
        if (isStartDate) {
            startDate.time = startDate.setTime(hourOfDay, minute).time
            observableTime.value = getTime(startDate)

            resetEndDateIfNeeded()
        }
        else {
            endDate.time = endDate.setTime(hourOfDay, minute).time
            observableTime.value = getTime(endDate)

            resetStartDateIfNeeded()
        }
        val (dayTimeChoices, timeChoices) = getDayTimeChoices(if (isStartDate) startDate else endDate)
        observableDayTimeChoice.value = dayTimeChoices
        observableTimeChoices.value = timeChoices
    }

    fun clearCurrentDate() {
        if (isStartDate) {
            startDate.time = 0L
            observableTime.value = getTime(startDate)
            observableDate.value = getDate(startDate)
        }
        else {
            endDate.time = 0L
            observableTime.value = getTime(endDate)
            observableDate.value = getDate(endDate)
        }
    }

    fun selectDayTimeChoice(choice: Int) {
        if (observableDayTimeChoice.value == choice) {
            val (dayTimeChoices, timeChoices) = getDayTimeChoices(if (isStartDate) startDate else endDate)
            observableDayTimeChoice.value = dayTimeChoices
            observableTimeChoices.value = timeChoices
            return
        }
        else {
            if (isStartDate) {
                startDate.time = when (choice) {
                    MORNING_CHOICE -> startDate.setTime(DEFAULT_MORNING_HOUR, 0).time
                    AFTERNOON_CHOICE -> startDate.setTime(DEFAULT_AFTERNOON_HOUR, 0).time
                    EVENING_CHOICE -> startDate.setTime(DEFAULT_EVENING_HOUR, 0).time
                    NIGHT_CHOICE -> startDate.setTime(DEFAULT_NIGHT_HOUR, 0).time
                    else -> startDate.time
                }
                observableTime.value = getTime(startDate)
                val (dayTimeChoices, timeChoices) = getDayTimeChoices(startDate)
                observableDayTimeChoice.value = dayTimeChoices
                observableTimeChoices.value = timeChoices

                resetEndDateIfNeeded()
            }
            else {
                endDate.time = when (choice) {
                    MORNING_CHOICE -> endDate.setTime(DEFAULT_MORNING_HOUR, 0).time
                    AFTERNOON_CHOICE -> endDate.setTime(DEFAULT_AFTERNOON_HOUR, 0).time
                    EVENING_CHOICE -> endDate.setTime(DEFAULT_EVENING_HOUR, 0).time
                    NIGHT_CHOICE -> endDate.setTime(DEFAULT_NIGHT_HOUR, 0).time
                    else -> endDate.time
                }
                observableTime.value = getTime(endDate)
                val (dayTimeChoices, timeChoices) = getDayTimeChoices(endDate)
                observableDayTimeChoice.value = dayTimeChoices
                observableTimeChoices.value = timeChoices

                resetStartDateIfNeeded()
            }
        }
    }

    fun showTimePicker() {
        observableTimePicker.value = if (isStartDate) {
            if (startDate.time == 0L) Date().roundToNextHour()
            else startDate
        }
        else {
            if (endDate.time == 0L) Date().roundToNextHour()
            else endDate
        }
    }

    fun selectTimeChoice(choice: TimeChoiceUiModel) {
        observableSelectedTimeChoice.value = choice
        val (hour, minute) = choice.date.hourToMinute
        if (isStartDate) {
            startDate.time = startDate.setTime(hour, minute).time
            observableTime.value = getTime(startDate)

            resetEndDateIfNeeded()
        }
        else {
            endDate.time = endDate.setTime(hour, minute).time
            observableTime.value = getTime(endDate)

            resetStartDateIfNeeded()
        }
    }

    fun requestDateTimeChange() {
        val currentDate = if (isStartDate) startDate else endDate
        observableDateChoices.value = getDateChoices(currentDate, 0)
        val (dayTimeChoices, timeChoices) = getDayTimeChoices(currentDate)
        observableDayTimeChoice.value = dayTimeChoices
        observableTimeChoices.value = timeChoices
    }

    fun toggleFullDay() {
        observableFullDay.value = observableFullDay.value?.not()
    }

    private fun getDate(date: Date): AndroidString? = if (date.time == 0L) null
        else AndroidString(text = date.toDateString(false))

    private fun getTime(date: Date): AndroidString? = if (date.time == 0L) null
        else AndroidString(text = date.toTimeString())

    private fun getDateChoices(firstDate: Date, selectedPosition: Int): Array<DateChoiceUiModel> {
        return Array(SIMPLE_VIEW_NUM_DATE_CHOICES) {
            val curr = firstDate.addDay(it)
            DateChoiceUiModel(
                AndroidString(text = curr.shortWeekday()),
                AndroidString(text = curr.dayOfMonth()),
                curr,
                if (it == selectedPosition) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE
            )
        }
    }

    /**
     * Morning is choice 0
     * Afternoon is choice 2
     * Evening is choice 3
     * Night is choice 4
     *
     * returns -1 when the date is not in any predefined time of day
     */
    private fun getDayTimeChoices(date: Date, autoSelectFromDate: Boolean = true): Pair<Int, ArrayList<TimeChoiceUiModel>> {
        val timeChoices = ArrayList<TimeChoiceUiModel>()
        return if (!autoSelectFromDate) NOT_SELECTED_INDEX to timeChoices.apply { setTimeChoices(this, date, DEFAULT_MORNING_HOUR_RANGE) }
        else when (date.hourOfDay) {
            in DEFAULT_MORNING_HOUR_RANGE -> MORNING_CHOICE to timeChoices.apply { setTimeChoices(this, date, DEFAULT_MORNING_HOUR_RANGE) }
            in DEFAULT_AFTERNOON_HOUR_RANGE -> AFTERNOON_CHOICE to timeChoices.apply { setTimeChoices(this, date, DEFAULT_AFTERNOON_HOUR_RANGE) }
            in DEFAULT_EVENING_HOUR_RANGE -> EVENING_CHOICE to timeChoices.apply { setTimeChoices(this, date, DEFAULT_EVENING_HOUR_RANGE) }
            in DEFAULT_NIGHT_HOUR_RANGE -> NIGHT_CHOICE to timeChoices.apply { setTimeChoices(this, date, DEFAULT_NIGHT_HOUR_RANGE) }
            else -> 3 to timeChoices.apply { setTimeChoices(this, date, DEFAULT_NIGHT_HOUR_RANGE) }
        }
    }

    private fun setTimeChoices(choices: ArrayList<TimeChoiceUiModel>, date: Date, range: IntRange) {
        for (i in range) {
            val choice1 = date.setTime(i, 0)
            choices.add(TimeChoiceUiModel(AndroidString(text = choice1.toTimeString()), choice1,
                if (date == choice1 && date.time != 0L) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE))
        }
    }

    fun confirmSelection() {
        observableFinishEvent.value = ConfirmDateTimeEvent(
            start = if (startDate.time == 0L) null else startDate,
            end = if (endDate.time == 0L) null else endDate,
            isFullDay = observableFullDay.value ?: false
        )
    }

    fun toggleStartOrEndDate(isStartDate: Boolean) {
        this.isStartDate = isStartDate
    }

    fun getObservableDate(): LiveData<AndroidString?> = observableDate

    fun getObservableTime(): LiveData<AndroidString?> = observableTime

    fun getObservableFullDay(): LiveData<Boolean> = observableFullDay

    fun getObservableSimpleDateChoices(): LiveData<Array<DateChoiceUiModel>> = observableDateChoices

    fun getObservableDayTimeChoice(): LiveData<Int> = observableDayTimeChoice

    fun getObservableTimeChoices(): LiveData<ArrayList<TimeChoiceUiModel>> = observableTimeChoices

    fun getObservableTimeChoice(): LiveData<TimeChoiceUiModel> = observableSelectedTimeChoice

    fun getObservableFinishEvent(): LiveData<ConfirmDateTimeEvent> = observableFinishEvent

    fun getCurrentDate(): Date = startDate

    fun getMinDate(): Date? = minDate

    fun getObservableTimePicker(): LiveData<Date> = observableTimePicker
}
