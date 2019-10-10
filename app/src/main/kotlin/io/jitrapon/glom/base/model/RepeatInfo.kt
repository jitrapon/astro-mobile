package io.jitrapon.glom.base.model

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.util.AppLogger
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

const val UNTIL_FOREVER = 0L
const val MAX_ALLOW_OCCURENCE = 999
const val REPEAT_ON_LAST_DAY_OF_MONTH = 0
const val REPEAT_ON_SAME_DATE =
    1                           // i.e. 17th of every month, 17th Jan of every year
const val REPEAT_ON_SAME_DAY_OF_WEEK =
    2                    // i.e. third Tuesday of the month, last Wednesday of January
const val REPEAT_ON_LAST_WEEKDAY =
    3                        // i.e. last weekday of the month, last weekday of January
const val REPEAT_ON_LAST_WEEKEND =
    4                        // i.e. last weekend day of the month, last weekend day of January

/**
 * @author Jitrapon Tiachunpun
 */
data class RepeatInfo(
    val rrule: String?,
    val occurenceId: Long?,
    var isReschedule: Boolean?,
    val unit: Int,
    val interval: Long,
    val until: Long,
    val meta: List<Int>?,
    var firstInstanceStartTime: Long = 0L,
    val instanceStartTime: Long? = null,
    val instanceIsFullDay: Boolean? = null,
    val instanceSyncId: String? = null,
    var editMode: RecurringSaveOption? = null,
    override var retrievedTime: Date? = null,
    override val error: Throwable? = null
) : DataModel {

    enum class TimeUnit(val value: Int) {
        DAY(1), WEEK(2), MONTH(3), YEAR(4)
    }

    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readLong().let {
            if (it == -1L) null else it
        },
        parcel.readInt().let {
            if (it == -1) null else it == 1
        },
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        ArrayList<Int>().let {
            parcel.readList(it, null)
            if (it.isEmpty()) null else it
        },
        parcel.readLong(),
        parcel.readLong().let {
            if (it == -1L) null else it
        },
        parcel.readInt().let {
            if (it == -1) null else it == 1
        },
        parcel.readString(),
        parcel.readInt().let {
            RecurringSaveOption.values().associateBy(RecurringSaveOption::index).getOrDefault(it, null)
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(rrule)
        parcel.writeLong(occurenceId ?: -1L)
        parcel.writeInt(
            if (isReschedule == null) -1
            else {
                if (isReschedule == true) 1 else 0
            }
        )
        parcel.writeInt(unit)
        parcel.writeLong(interval)
        parcel.writeLong(until)
        parcel.writeList(meta)
        parcel.writeLong(firstInstanceStartTime)
        parcel.writeLong(instanceStartTime ?: -1L)
        parcel.writeInt(
            if (instanceIsFullDay == null) -1
            else {
                if (instanceIsFullDay) 1 else 0
            }
        )
        parcel.writeString(instanceSyncId)
        parcel.writeInt(editMode?.index ?: -1)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RepeatInfo> {
        override fun createFromParcel(parcel: Parcel): RepeatInfo = RepeatInfo(parcel)
        override fun newArray(size: Int): Array<RepeatInfo?> = arrayOfNulls(size)
    }
}

enum class RecurringSaveOption(val index: Int) {
    SINGLE(0), ALL(1)
}

/**
 * Converts an RRULE-formatted string to a RepeatInfo instance
 */
fun String?.toRepeatInfo(
    occurrenceId: Long?,
    isRescheduled: Boolean?,
    firstStartTime: Long,
    originalStartTime: Long?,
    originalIsFullDay: Boolean?,
    instanceSyncId: String?
): RepeatInfo? {
    this ?: return null
    val tokens = split(";")
    val unit = tokens.find { it.startsWith("FREQ=") }?.removePrefix("FREQ=")?.let {
        when (it) {
            "DAILY" -> RepeatInfo.TimeUnit.DAY
            "WEEKLY" -> RepeatInfo.TimeUnit.WEEK
            "MONTHLY" -> RepeatInfo.TimeUnit.MONTH
            "YEARLY" -> RepeatInfo.TimeUnit.YEAR
            else -> null
        }
    }?.value ?: return null
    val interval = tokens.find { it.startsWith("INTERVAL=") }?.removePrefix("INTERVAL=")
        ?.toLongOrNull() ?: 1
    var until = UNTIL_FOREVER
    val rruleDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ENGLISH)
    val untilTimeMs = tokens.find { it.startsWith("UNTIL=") }?.removePrefix("UNTIL=")?.let {
        return@let try {
            rruleDateFormat.parse(it).time
        }
        catch (ex: Exception) {
            AppLogger.e(ex)
            null
        }
    }
    val untilCount = tokens.find { it.startsWith("COUNT=") }?.removePrefix("COUNT=")?.toLongOrNull()
    if (untilTimeMs != null) {
        until = untilTimeMs
    }
    if (untilCount != null) {
        until = untilCount
    }
    val meta = arrayListOf<Int>()
    if (unit == RepeatInfo.TimeUnit.WEEK.value) {
        tokens.find { it.startsWith("BYDAY=") }?.removePrefix("BYDAY=")?.let {
            val repeatInfoDayValues = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
            val days = it.split(",")
            for (day in days) {
                meta.add(repeatInfoDayValues.indexOf(day))
            }
        }
    }
    else if (unit == RepeatInfo.TimeUnit.MONTH.value) {
        tokens.find { it.startsWith("BYMONTHDAY=") }?.removePrefix("BYMONTHDAY=")?.let {
            if (it.toIntOrNull() == -1) {
                meta.add(REPEAT_ON_LAST_DAY_OF_MONTH)
            }
            else {
                meta.add(REPEAT_ON_SAME_DATE)
            }
        }
        tokens.find { it.startsWith("BYSETPOS=") }?.let {
            meta.add(REPEAT_ON_SAME_DAY_OF_WEEK)
        }
    }
    return RepeatInfo(
        this,
        occurrenceId,
        isRescheduled,
        unit,
        interval,
        until,
        meta,
        firstStartTime,
        originalStartTime,
        originalIsFullDay,
        instanceSyncId
    )
}
