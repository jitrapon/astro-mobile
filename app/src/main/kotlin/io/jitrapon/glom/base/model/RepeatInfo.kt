package io.jitrapon.glom.base.model

import android.os.Parcel
import android.os.Parcelable
import java.util.ArrayList
import java.util.Date

const val UNTIL_FOREVER = 0L
const val MAX_ALLOW_OCCURENCE = 999

const val REPEAT_ON_LAST_DAY_OF_MONTH = 0
const val REPEAT_ON_SAME_DATE = 1                           // i.e. 17th of every month, 17th Jan of every year
const val REPEAT_ON_SAME_DAY_OF_WEEK = 2                    // i.e. third Tuesday of the month, last Wednesday of January
const val REPEAT_ON_LAST_WEEKDAY = 3                        // i.e. last weekday of the month, last weekday of January
const val REPEAT_ON_LAST_WEEKEND = 4                        // i.e. last weekend day of the month, last weekend day of January

/**
 * @author Jitrapon Tiachunpun
 */
data class RepeatInfo(val rrule: String?,
                      val occurenceId: Int?,
                      val isReschedule: Boolean?,
                      val unit: Int,
                      val interval: Long,
                      val until: Long,
                      val meta: List<Int>?,
                      override var retrievedTime: Date? = null,
                      override val error: Throwable? = null) : DataModel {

    enum class TimeUnit(val value: Int) {
        DAY(1), WEEK(2), MONTH(3), YEAR(4)
    }

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readInt().let {
                if (it == -1) null else it
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
            })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(rrule)
        parcel.writeInt(occurenceId ?: -1)
        parcel.writeInt(if (isReschedule == null) -1 else {
            if (isReschedule) 1 else 0
        })
        parcel.writeInt(unit)
        parcel.writeLong(interval)
        parcel.writeLong(until)
        parcel.writeList(meta)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RepeatInfo> {
        override fun createFromParcel(parcel: Parcel): RepeatInfo = RepeatInfo(parcel)

        override fun newArray(size: Int): Array<RepeatInfo?> = arrayOfNulls(size)
    }
}

fun String?.toRepeatInfo(): RepeatInfo? {

    return null
}
