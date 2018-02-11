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
