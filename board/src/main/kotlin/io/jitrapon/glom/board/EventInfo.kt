package io.jitrapon.glom.board

import android.os.Parcel
import android.os.Parcelable
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */

data class EventInfo(val eventName: String,
                     val startTime: Long?,
                     val endTime: Long?,
                     val location: EventLocation?,
                     val note: String?,
                     val timeZone: String?,
                     val isFullDay: Boolean,
                     val repeatInfo: RepeatInfo?,
                     val datePollStatus: Boolean,
                     val placePollStatus: Boolean,
                     val attendees: List<String>,
                     override val retrievedTime: Date? = null,
                     override val error: Throwable? = null): BoardItemInfo {

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readLong().let {
                if (it == -1L) null else it
            },
            parcel.readLong().let {
                if (it == -1L) null else it
            },
            parcel.readParcelable(EventLocation::class.java.classLoader),
            parcel.readString(),
            parcel.readString(),
            parcel.readByte() != 0.toByte(),
            parcel.readParcelable(RepeatInfo::class.java.classLoader),
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte(),
            parcel.createStringArrayList())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(eventName)
        parcel.writeLong(startTime ?: -1L)
        parcel.writeLong(endTime ?: -1L)
        parcel.writeParcelable(location, flags)
        parcel.writeString(note)
        parcel.writeString(timeZone)
        parcel.writeByte(if (isFullDay) 1 else 0)
        parcel.writeParcelable(repeatInfo, flags)
        parcel.writeByte(if (datePollStatus) 1 else 0)
        parcel.writeByte(if (placePollStatus) 1 else 0)
        parcel.writeStringList(attendees)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EventInfo> {
        override fun createFromParcel(parcel: Parcel): EventInfo = EventInfo(parcel)

        override fun newArray(size: Int): Array<EventInfo?> = arrayOfNulls(size)
    }
}
