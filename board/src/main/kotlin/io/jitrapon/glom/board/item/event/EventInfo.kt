package io.jitrapon.glom.board.item.event

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.board.item.BoardItemInfo
import io.jitrapon.glom.board.item.event.calendar.DeviceCalendar
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
                     var source: EventSource,
                     override var retrievedTime: Date? = null,
                     override var error: Throwable? = null): BoardItemInfo {

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
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
            parcel.createStringArrayList()!!,
            parcel.readParcelable(EventSource::class.java.classLoader)!!)

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
        parcel.writeParcelable(source, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EventInfo> {
        override fun createFromParcel(parcel: Parcel): EventInfo = EventInfo(parcel)

        override fun newArray(size: Int): Array<EventInfo?> = arrayOfNulls(size)
    }
}

data class EventSource(val sourceIconUrl: String?,
                       val calendar: DeviceCalendar?,
                       val description: String?,
                       override var retrievedTime: Date? = null,
                       override val error: Throwable? = null
): DataModel {
    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readParcelable(DeviceCalendar::class.java.classLoader),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sourceIconUrl)
        parcel.writeParcelable(calendar, flags)
        parcel.writeString(description)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<EventSource> {
        override fun createFromParcel(parcel: Parcel): EventSource {
            return EventSource(parcel)
        }

        override fun newArray(size: Int): Array<EventSource?> {
            return arrayOfNulls(size)
        }
    }

    fun isWritable(): Boolean = (calendar != null && calendar.isWritable) || calendar == null

    fun isEmpty(): Boolean = calendar == null && sourceIconUrl == null && description == null
}
