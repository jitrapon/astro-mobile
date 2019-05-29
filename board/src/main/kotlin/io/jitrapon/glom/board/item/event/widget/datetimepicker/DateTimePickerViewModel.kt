package io.jitrapon.glom.board.item.event.widget.datetimepicker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.DateTimePickerUiModel
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.util.addDay
import io.jitrapon.glom.base.util.dayOfMonth
import io.jitrapon.glom.base.util.hourOfDay
import io.jitrapon.glom.base.util.hourToMinute
import io.jitrapon.glom.base.util.setTime
import io.jitrapon.glom.base.util.shortWeekday
import io.jitrapon.glom.base.util.toDateString
import io.jitrapon.glom.base.util.toDayMonthYear
import io.jitrapon.glom.base.util.toTimeString
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.Date

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

    private val currentDate: Date = Date()
    private val defaultDate: Date = Date()
    private var minDate: Date? = null

    fun setDateTime(uiModel: DateTimePickerUiModel?) {
        uiModel ?: return
        currentDate.time = uiModel.defaultDate.time
        defaultDate.time = currentDate.time
        minDate = uiModel.minDate
        observableDate.value = getDate(currentDate)
        observableTime.value = getTime(currentDate)
        observableFullDay.value = uiModel.isFullDay
        observableDateChoices.value = getDateChoices(currentDate, 0)
        val (dayTimeChoices, timeChoices) = getDayTimeChoices(currentDate)
        observableDayTimeChoice.value = dayTimeChoices
        observableTimeChoices.value = timeChoices
    }

    fun selectDateChoice(position: Int) {
        if (position < 0 || position > SIMPLE_VIEW_NUM_DATE_CHOICES - 1) return

        observableDateChoices.value = getDateChoices(defaultDate, position)
        observableDateChoices.value?.get(position)?.date?.let {
            val (day, month, year) = it.toDayMonthYear()
            currentDate.setTime(year, month, day)
            observableDate.value = getDate(currentDate)
        }
    }

    fun selectCalendarDate(date: Date) {
        val (day, month, year) = date.toDayMonthYear()
        currentDate.time = currentDate.setTime(year, month, day).time
        defaultDate.time = currentDate.time
        observableDate.value = getDate(currentDate)
    }

    fun setTime(hourOfDay: Int, minute: Int) {
        currentDate.time = currentDate.setTime(hourOfDay, minute).time
        observableTime.value = getTime(currentDate)
    }

    fun selectDayTimeChoice(choice: Int) {
        if (observableDayTimeChoice.value == choice) {
            val (dayTimeChoices, timeChoices) = getDayTimeChoices(currentDate)
            observableDayTimeChoice.value = dayTimeChoices
            observableTimeChoices.value = timeChoices
            return
        }
        else {
            currentDate.time = when (choice) {
                0 -> currentDate.setTime(8, 0).time
                1 -> currentDate.setTime(14, 0).time
                2 -> currentDate.setTime(18, 0).time
                3 -> currentDate.setTime(22, 0).time
                else -> currentDate.time
            }
            observableTime.value = getTime(currentDate)
            val (dayTimeChoices, timeChoices) = getDayTimeChoices(currentDate)
            observableDayTimeChoice.value = dayTimeChoices
            observableTimeChoices.value = timeChoices
        }
    }

    fun selectTimeChoice(choice: TimeChoiceUiModel) {
        observableSelectedTimeChoice.value = choice
        val (hour, minute) = choice.date.hourToMinute
        currentDate.time = currentDate.setTime(hour, minute).time
        observableTime.value = getTime(currentDate)
    }

    fun requestDateTimeChange() {
        observableDateChoices.value = getDateChoices(currentDate, 0)
        val (dayTimeChoices, timeChoices) = getDayTimeChoices(currentDate)
        observableDayTimeChoice.value = dayTimeChoices
        observableTimeChoices.value = timeChoices
    }

    fun toggleFullDay() {
        observableFullDay.value = observableFullDay.value?.not()
    }

    private fun getDate(date: Date): AndroidString = AndroidString(text = date.toDateString(true))

    private fun getTime(date: Date): AndroidString = AndroidString(text = date.toTimeString())

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
            in 6..11 -> 0 to timeChoices.apply { setTimeChoices(this, date, 6, 11) }
            in 12..16 -> 1 to timeChoices.apply { setTimeChoices(this, date, 12, 16) }
            in 17..20 -> 2 to timeChoices.apply { setTimeChoices(this, date, 17, 20) }
            in 21..23 -> 3 to timeChoices.apply { setTimeChoices(this, date, 21, 23) }
            else -> -1 to timeChoices
        }
    }

    private fun setTimeChoices(choices: ArrayList<TimeChoiceUiModel>, date: Date, startHour: Int, endHour: Int) {
        for (i in startHour..endHour) {
            val choice1 = date.setTime(i, 0)
            choices.add(TimeChoiceUiModel(AndroidString(text = choice1.toTimeString()), choice1,
                if (date == choice1) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE))
        }
    }

    fun getObservableDate(): LiveData<AndroidString> = observableDate

    fun getObservableTime(): LiveData<AndroidString> = observableTime

    fun getObservableFullDay(): LiveData<Boolean> = observableFullDay

    fun getObservableSimpleDateChoices(): LiveData<Array<DateChoiceUiModel>> = observableDateChoices

    fun getObservableDayTimeChoice(): LiveData<Int> = observableDayTimeChoice

    fun getObservableTimeChoices(): LiveData<ArrayList<TimeChoiceUiModel>> = observableTimeChoices

    fun getObservableTimeChoice(): LiveData<TimeChoiceUiModel> = observableSelectedTimeChoice

    fun getCurrentDate(): Date = currentDate

    fun getMinDate(): Date? = minDate

    fun getCurrentTime(): Pair<Int, Int> = currentDate.hourToMinute
}
