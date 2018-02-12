package io.jitrapon.glom.base.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

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
}

fun Date.toDateString(showYear: Boolean = true): String = if (showYear) DateTimeFormat.DATE_FORMAT_WITH_YEAR.get().format(this) else
    DateTimeFormat.DATE_FORMAT_NO_YEAR.get().format(this)

fun Date.toTimeString(): String = DateTimeFormat.TIME_FORMAT.get().format(this)

fun Date.add(field: Int, amount: Int): Date{
    val cal = Calendar.getInstance()
    cal.time=this
    cal.add(field, amount)

    this.time = cal.time.time

    cal.clear()

    return this
}

fun Date.addYears(years: Int): Date{
    return add(Calendar.YEAR, years)
}
fun Date.addMonths(months: Int): Date {
    return add(Calendar.MONTH, months)
}
fun Date.addDays(days: Int): Date{
    return add(Calendar.DAY_OF_MONTH, days)
}
fun Date.addHours(hours: Int): Date{
    return add(Calendar.HOUR_OF_DAY, hours)
}
fun Date.addMinutes(minutes: Int): Date{
    return add(Calendar.MINUTE, minutes)
}
fun Date.addSeconds(seconds: Int): Date{
    return add(Calendar.SECOND, seconds)
}
