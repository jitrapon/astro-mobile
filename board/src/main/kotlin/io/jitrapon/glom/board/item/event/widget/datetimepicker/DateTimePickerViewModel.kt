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

    private var isStartDate: Boolean = false
    private var startDate: Date = Date()
    private val endDate: Date = Date()
    private val firstDate: Date = Date()
    private var minDate: Date? = null
    
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
        startDate.time = uiModel.startDate?.time ?: 0L
        endDate.time = uiModel.endDate?.time ?: 0L
        minDate = uiModel.minDate
        observableFullDay.value = uiModel.isFullDay
        resetDateTime()
    }

    private fun resetDateTime() {
        val currentDate = if (isStartDate) startDate else endDate   //TODO initialize startDate as firstDate and endDate
        firstDate.time = if (currentDate.time == 0L) Date().time else currentDate.time
        if (isStartDate) startDate.time = firstDate.time else endDate.time = firstDate.time
        //TODO

        observableDate.value = getDate(firstDate)
        observableTime.value = getTime(firstDate)
        observableDateChoices.value = getDateChoices(firstDate, 0)
        val (dayTimeChoices, timeChoices) = getDayTimeChoices(firstDate)
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
            } 
            else {
                endDate.setTime(year, month, day)
                observableDate.value = getDate(endDate)
            }
        }
    }

    fun selectCalendarDate(date: Date) {
        val (day, month, year) = date.toDayMonthYear()
        if (isStartDate) {
            startDate.time = startDate.setTime(year, month, day).time
            firstDate.time = startDate.time
            observableDate.value = getDate(startDate)
        }
        else {
            endDate.time = endDate.setTime(year, month, day).time
            firstDate.time = endDate.time
            observableDate.value = getDate(endDate)
        }
    }

    fun setTime(hourOfDay: Int, minute: Int) {
        if (isStartDate) {
            startDate.time = startDate.setTime(hourOfDay, minute).time
            observableTime.value = getTime(startDate)
        }
        else {
            endDate.time = endDate.setTime(hourOfDay, minute).time
            observableTime.value = getTime(endDate)
        }
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
                    0 -> startDate.setTime(DEFAULT_MORNING_HOUR, 0).time
                    1 -> startDate.setTime(DEFAULT_AFTERNOON_HOUR, 0).time
                    2 -> startDate.setTime(DEFAULT_EVENING_HOUR, 0).time
                    3 -> startDate.setTime(DEFAULT_NIGHT_HOUR, 0).time
                    else -> startDate.time
                }
                observableTime.value = getTime(startDate)
                val (dayTimeChoices, timeChoices) = getDayTimeChoices(startDate)
                observableDayTimeChoice.value = dayTimeChoices
                observableTimeChoices.value = timeChoices
            }
            else {
                endDate.time = when (choice) {
                    0 -> endDate.setTime(DEFAULT_MORNING_HOUR, 0).time
                    1 -> endDate.setTime(DEFAULT_AFTERNOON_HOUR, 0).time
                    2 -> endDate.setTime(DEFAULT_EVENING_HOUR, 0).time
                    3 -> endDate.setTime(DEFAULT_NIGHT_HOUR, 0).time
                    else -> endDate.time
                }
                observableTime.value = getTime(endDate)
                val (dayTimeChoices, timeChoices) = getDayTimeChoices(endDate)
                observableDayTimeChoice.value = dayTimeChoices
                observableTimeChoices.value = timeChoices
            }
        }
    }

    fun selectTimeChoice(choice: TimeChoiceUiModel) {
        observableSelectedTimeChoice.value = choice
        val (hour, minute) = choice.date.hourToMinute
        if (isStartDate) {
            startDate.time = startDate.setTime(hour, minute).time
            observableTime.value = getTime(startDate)
        }
        else {
            endDate.time = endDate.setTime(hour, minute).time
            observableTime.value = getTime(endDate)
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

    private fun getDate(date: Date): AndroidString = if (date.time == 0L) AndroidString(text = null)
        else AndroidString(text = date.toDateString(false))

    private fun getTime(date: Date): AndroidString = if (date.time == 0L) AndroidString(text = null)
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
    private fun getDayTimeChoices(date: Date): Pair<Int, ArrayList<TimeChoiceUiModel>> {
        val timeChoices = ArrayList<TimeChoiceUiModel>()
        return when (date.hourOfDay) {
            in DEFAULT_MORNING_HOUR_RANGE -> 0 to timeChoices.apply { setTimeChoices(this, date, DEFAULT_MORNING_HOUR_RANGE) }
            in DEFAULT_AFTERNOON_HOUR_RANGE -> 1 to timeChoices.apply { setTimeChoices(this, date, DEFAULT_AFTERNOON_HOUR_RANGE) }
            in DEFAULT_EVENING_HOUR_RANGE -> 2 to timeChoices.apply { setTimeChoices(this, date, DEFAULT_EVENING_HOUR_RANGE) }
            in DEFAULT_NIGHT_HOUR_RANGE -> 3 to timeChoices.apply { setTimeChoices(this, date, DEFAULT_NIGHT_HOUR_RANGE) }
            else -> 3 to timeChoices.apply { setTimeChoices(this, date, DEFAULT_NIGHT_HOUR_RANGE) }
        }
    }

    private fun setTimeChoices(choices: ArrayList<TimeChoiceUiModel>, date: Date, range: IntRange) {
        for (i in range) {
            val choice1 = date.setTime(i, 0)
            choices.add(TimeChoiceUiModel(AndroidString(text = choice1.toTimeString()), choice1,
                if (date == choice1) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE))
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

    fun getObservableDate(): LiveData<AndroidString> = observableDate

    fun getObservableTime(): LiveData<AndroidString> = observableTime

    fun getObservableFullDay(): LiveData<Boolean> = observableFullDay

    fun getObservableSimpleDateChoices(): LiveData<Array<DateChoiceUiModel>> = observableDateChoices

    fun getObservableDayTimeChoice(): LiveData<Int> = observableDayTimeChoice

    fun getObservableTimeChoices(): LiveData<ArrayList<TimeChoiceUiModel>> = observableTimeChoices

    fun getObservableTimeChoice(): LiveData<TimeChoiceUiModel> = observableSelectedTimeChoice

    fun getObservableFinishEvent(): LiveData<ConfirmDateTimeEvent> = observableFinishEvent

    fun getCurrentDate(): Date = startDate

    fun getMinDate(): Date? = minDate

    fun getCurrentTime(): Pair<Int, Int> = startDate.hourToMinute
}
