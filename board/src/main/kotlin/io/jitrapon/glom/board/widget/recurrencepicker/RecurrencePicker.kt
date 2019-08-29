package io.jitrapon.glom.board.widget.recurrencepicker

import androidx.fragment.app.FragmentManager
import com.maltaisn.recurpicker.RRuleFormat
import com.maltaisn.recurpicker.Recurrence
import com.maltaisn.recurpicker.RecurrencePickerDialog
import io.jitrapon.glom.base.model.MAX_ALLOW_OCCURENCE
import io.jitrapon.glom.base.model.REPEAT_ON_LAST_DAY_OF_MONTH
import io.jitrapon.glom.base.model.REPEAT_ON_SAME_DATE
import io.jitrapon.glom.base.model.REPEAT_ON_SAME_DAY_OF_WEEK
import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.base.model.UNTIL_FOREVER
import io.jitrapon.glom.base.util.AppLogger
import java.util.ArrayList

class RecurrencePicker : RecurrencePickerDialog(),
    RecurrencePickerDialog.RecurrenceSelectedCallback {

    private var onRecurrencePicked: ((RepeatInfo?) -> Unit)? = null

    override fun onRecurrencePickerSelected(r: Recurrence?) {
        onRecurrencePicked?.invoke(r.toRepeatInfo())
    }

    override fun onRecurrencePickerCancelled(r: Recurrence?) {
    }

    fun show(
        supportFragmentManager: FragmentManager,
        uiModel: RecurrencePickerUiModel,
        onRecurrencePicked: (RepeatInfo?) -> Unit
    ) {
        RecurrencePickerDialog().apply {
            setRecurrence(
                uiModel.event.itemInfo.repeatInfo.toRecurrence(),
                uiModel.event.itemInfo.startTime ?: System.currentTimeMillis()
            )
            setMaxEventCount(MAX_ALLOW_OCCURENCE)
            show(supportFragmentManager, "recur_picker")
        }
        this.onRecurrencePicked = onRecurrencePicked
    }

    private fun RepeatInfo?.toRecurrence(): Recurrence? {
        this ?: return null
        return Recurrence(firstStartTime, when (unit) {
            RepeatInfo.TimeUnit.DAY.value -> Recurrence.DAILY
            RepeatInfo.TimeUnit.WEEK.value -> Recurrence.WEEKLY
            RepeatInfo.TimeUnit.MONTH.value -> Recurrence.MONTHLY
            RepeatInfo.TimeUnit.YEAR.value -> Recurrence.YEARLY
            else -> Recurrence.NONE
        }).apply {
            frequency = this@toRecurrence.interval.toInt()
            if (period == Recurrence.WEEKLY) {
                val repeatDays = this@toRecurrence.meta
                if (!repeatDays.isNullOrEmpty()) {
                    var days = 0
                    for (day in repeatDays) {
                        days += 1 shl day + 1
                    }
                    setWeeklySetting(days)
                }
            }
            else if (period == Recurrence.MONTHLY) {
                val meta = this@toRecurrence.meta?.getOrNull(0)
                if (meta != null) {
                    setMonthlySetting(when (meta) {
                        REPEAT_ON_SAME_DATE -> Recurrence.SAME_DAY_OF_MONTH
                        REPEAT_ON_SAME_DAY_OF_WEEK -> Recurrence.SAME_DAY_OF_WEEK
                        REPEAT_ON_LAST_DAY_OF_MONTH -> Recurrence.LAST_DAY_OF_MONTH
                        else -> -1
                    })
                }
            }
            val until = this@toRecurrence.until
            when {
                until == UNTIL_FOREVER -> setEndNever()
                until <= MAX_ALLOW_OCCURENCE -> setEndByCount(until.toInt())
                else -> setEndByDate(until)
            }
        }
    }

    private fun Recurrence?.toRepeatInfo(): RepeatInfo? {
        this ?: return null

        AppLogger.d(
            "Recurrence: " +
                    "startDate=${startDate}, endDate=${endDate}, daySetting=${daySetting}, " +
                    "endCount=${endCount}, endType=${endType}, frequency=${frequency}, isDefault=${isDefault}, " +
                    "period=${period}"
        )
        val rrule: String? = RRuleFormat.format(this)
        AppLogger.d("RRULE: $rrule")
        val unit = when (period) {
            Recurrence.DAILY -> RepeatInfo.TimeUnit.DAY
            Recurrence.WEEKLY -> RepeatInfo.TimeUnit.WEEK
            Recurrence.MONTHLY -> RepeatInfo.TimeUnit.MONTH
            Recurrence.YEARLY -> RepeatInfo.TimeUnit.YEAR
            else -> null
        }
        val untilTime = if (endCount == -1) {
            if (endDate == -1L) UNTIL_FOREVER else endDate
        }
        else {
            endCount.toLong()
        }
        val meta = when (period) {
            Recurrence.WEEKLY -> {
                val array = ArrayList<Int>()
                for (i in 0..6) {
                    if (isRepeatedOnDaysOfWeek(1 shl i + 1)) {
                        array.add(i)
                    }
                }
                array
            }
            Recurrence.MONTHLY -> {
                val array = ArrayList<Int>()
                when (daySetting) {
                    Recurrence.SAME_DAY_OF_MONTH -> array.add(REPEAT_ON_SAME_DATE)
                    Recurrence.SAME_DAY_OF_WEEK -> array.add(REPEAT_ON_SAME_DAY_OF_WEEK)
                    Recurrence.LAST_DAY_OF_MONTH -> array.add(REPEAT_ON_LAST_DAY_OF_MONTH)
                    else -> Unit
                }
                array
            }
            else -> null
        }
        return if (unit == null) null
        else RepeatInfo(
            rrule, null, null, unit.value, frequency.toLong(), untilTime, meta, startDate
        )
    }
}
