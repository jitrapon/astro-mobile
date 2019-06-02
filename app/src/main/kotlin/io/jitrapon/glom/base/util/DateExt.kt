package io.jitrapon.glom.base.util

import android.text.format.DateUtils
import io.jitrapon.glom.base.util.DateTimeFormat.MILLISECOND_IN_DAY
import io.jitrapon.glom.base.util.DateTimeFormat.RELATIVE_DAY_FORMAT
import io.jitrapon.glom.base.util.DateTimeFormat.TODAY
import io.jitrapon.glom.base.util.DateTimeFormat.TOMORROW
import io.jitrapon.glom.base.util.DateTimeFormat.YESTERDAY
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
* Created by Jitrapon
*/
object DateTimeFormat {

    @JvmField val DATE_FORMAT_WITH_YEAR = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat {
            return SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
        }
    }

    @JvmField val DATE_FORMAT_NO_YEAR = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat {
            return SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
        }
    }

    @JvmField val TIME_FORMAT = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat {
            return SimpleDateFormat("hh:mm a", Locale.getDefault())
        }
    }

    @JvmField val RELATIVE_DAY_FORMAT = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat {
            return SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        }
    }

    @JvmField val DATE_FORMAT_DAY_OF_WEEK = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat? {
            return SimpleDateFormat("EEE", Locale.getDefault())
        }
    }

    @JvmField val DATE_FORMAT_DAY_OF_MONTH = object : ThreadLocal<DateFormat>() {
        override fun initialValue(): DateFormat? {
            return SimpleDateFormat("dd", Locale.getDefault())
        }
    }

    const val MILLISECOND_IN_DAY = 1000 * 60 * 60 * 24
    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    const val TOMORROW = "Tomorrow"
}

fun Date.toDateString(showYear: Boolean = true): String = if (showYear) DateTimeFormat.DATE_FORMAT_WITH_YEAR.get()!!.format(this) else
    DateTimeFormat.DATE_FORMAT_NO_YEAR.get()!!.format(this)

fun Date.toRelativeDayString(): String {
    return when (Date().daysBetween(this)) {
        -1 -> YESTERDAY
        0 -> TODAY
        1 -> TOMORROW
        else -> RELATIVE_DAY_FORMAT.get()!!.format(this)
    }
}

fun Date.shortWeekday(): String = DateTimeFormat.DATE_FORMAT_DAY_OF_WEEK.get()!!.format(this)

fun Date.dayOfMonth(): String = DateTimeFormat.DATE_FORMAT_DAY_OF_MONTH.get()!!.format(this)

/**
 * Get days between two Date objects, comparing them by midnight time at 00:00:00
 */
fun Date.daysBetween(other: Date): Int {
    val cal = Calendar.getInstance()
    val startTimeMs = cal.run {
        time = this@daysBetween
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    val endTimeMs = cal.run {
        time = other
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    return (((endTimeMs - startTimeMs) / (MILLISECOND_IN_DAY)).toInt())
}

fun Date.toTimeString(): String = DateTimeFormat.TIME_FORMAT.get()!!.format(this)

fun Date.add(field: Int, amount: Int): Date {
    return Calendar.getInstance().let {
        it.time = this
        it.add(field, amount)
        Date(it.time.time)
    }
}

fun Date.isToday(): Boolean {
    return DateUtils.isToday(this.time)
}

fun Date.roundToNextHalfHour(): Date {
    return Calendar.getInstance().run {
        time = this@roundToNextHalfHour
        if (get(Calendar.MINUTE) < 30) {
            set(Calendar.MINUTE, 30)
        }
        else {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
        }
        Date(time.time)
    }
}

fun Date.roundToNextHour(): Date {
    return Calendar.getInstance().run {
        time = this@roundToNextHour
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        Date(time.time)
    }
}

fun Date.setTime(hour: Int, minute: Int, second: Int? = null, millisecond: Int? = null): Date {
    return Calendar.getInstance().run {
        time = this@setTime
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        second?.let {
            set(Calendar.SECOND, second)
        }
        millisecond?.let {
            set(Calendar.MILLISECOND, millisecond)
        }
        Date(time.time)
    }
}

fun Date.addDay(days: Int): Date {
    return add(Calendar.DAY_OF_MONTH, days)
}

fun Date.addHour(hours: Int): Date {
    return add(Calendar.HOUR_OF_DAY, hours)
}

fun Date.addMinute(minutes: Int): Date {
    return add(Calendar.MINUTE, minutes)
}

fun Date.addSecond(seconds: Int): Date {
    return add(Calendar.SECOND, seconds)
}

fun Date.toDayMonthYear(): Triple<Int, Int, Int> {
    return Calendar.getInstance().let {
        it.time = this
        Triple(it[Calendar.DAY_OF_MONTH], it[Calendar.MONTH], it[Calendar.YEAR])
    }
}

fun Date.withinDuration(other: Date, seconds: Int): Boolean {
    return TimeUnit.SECONDS.convert(Math.abs(time - other.time), TimeUnit.MILLISECONDS) <= seconds
}

fun Date.setTime(year: Int, month: Int, day: Int): Date {
    return Calendar.getInstance().let {
        it.time = this@setTime
        it.set(year, month, day)
        this@setTime.time = it.time.time
        it.time
    }
}

fun Date.sameDateAs(other: Date?): Boolean {
    val cal1 = Calendar.getInstance().apply { time = this@sameDateAs }
    val cal2 = Calendar.getInstance().apply { time = other }
    return (cal1[Calendar.DAY_OF_MONTH] == cal2[Calendar.DAY_OF_MONTH]) && (cal1[Calendar.MONTH] == cal2[Calendar.MONTH]) &&
            (cal1[Calendar.YEAR] == cal2[Calendar.YEAR])
}

val Date.hourOfDay: Int
    get() = Calendar.getInstance().run {
        time = this@hourOfDay
        get(Calendar.HOUR_OF_DAY)
    }

val Date.hourToMinute: Pair<Int, Int>
    get() = Calendar.getInstance().run {
        time = this@hourToMinute
        get(Calendar.HOUR_OF_DAY) to get(Calendar.MINUTE)
    }

val Date.secondToMillisecond: Pair<Int, Int>
    get() = Calendar.getInstance().run {
        time = this@secondToMillisecond
        get(Calendar.SECOND) to get(Calendar.MILLISECOND)
    }
