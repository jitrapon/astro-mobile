package io.jitrapon.glom.board.event

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.board.BoardItemInfo
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */

data class EventInfo(var eventName: String,
                     var startTime: Long?,
                     var endTime: Long?,
                     var location: EventLocation?,
                     var note: String?,
                     var timeZone: String?,
                     var isFullDay: Boolean,
                     var repeatInfo: RepeatInfo?,
                     var datePollStatus: Boolean,
                     var placePollStatus: Boolean,
                     var attendees: MutableList<String>,
                     override var retrievedTime: Date? = null,
                     override var error: Throwable? = null): BoardItemInfo {

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
