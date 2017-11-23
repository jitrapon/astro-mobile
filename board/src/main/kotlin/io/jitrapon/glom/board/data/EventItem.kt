package io.jitrapon.glom.board.data

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.data.DataModel
import java.util.*

/**
 * Represents an Event in the board
 *
 * @author Jitrapon Tiachunpun
 */
data class EventItem(override val itemType: Int,
                     override val itemId: String?,
                     override val createdTime: Long?,
                     override val updatedTime: Long?,
                     override val owners: List<String>,
                     override val itemInfo: EventInfo,
                     override val retrievedTime: Date? = Date(),
                     override val error: Throwable? = null) : BoardItem {

    constructor(parcel: Parcel, type: Int) : this(
            type,
            parcel.readString(),
            parcel.readLong().let {
                if (it == -1L) null
                else it
            },
            parcel.readLong().let {
                if (it == -1L) null
                else it
            },
            parcel.createStringArrayList(),
            parcel.readParcelable(EventInfo::class.java.classLoader),
            parcel.readLong().let {
                if (it == -1L) null
                else Date(it)
            })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(itemType)
        parcel.writeString(itemId)
        parcel.writeLong(createdTime ?: -1L)
        parcel.writeLong(updatedTime ?: -1L)
        parcel.writeStringList(owners)
        parcel.writeParcelable(itemInfo, flags)
        parcel.writeLong(retrievedTime?.time ?: -1L)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EventItem> {
        override fun createFromParcel(parcel: Parcel): EventItem = EventItem(parcel, parcel.readInt())

        override fun newArray(size: Int): Array<EventItem?> = arrayOfNulls(size)
    }
}

data class EventLocation(val latitude: Double?,
                         val longitude: Double?,
                         val googlePlaceId: String?,
                         val placeId: String?,
                         override val retrievedTime: Date? = null,
                         override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readDouble().let {
                if (it == -1.0) null else it
            },
            parcel.readDouble().let {
                if (it == -1.0) null else it
            },
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude ?: -1.0)
        parcel.writeDouble(longitude ?: -1.0)
        parcel.writeString(googlePlaceId)
        parcel.writeString(placeId)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EventLocation> {
        override fun createFromParcel(parcel: Parcel): EventLocation = EventLocation(parcel)

        override fun newArray(size: Int): Array<EventLocation?> = arrayOfNulls(size)
    }
}

data class RepeatInfo(val occurenceId: Int?,
                      val isReschedule: Boolean?,
                      val unit: Int,
                      val interval: Long,
                      val until: Long,
                      val meta: List<Int>?,
                      override val retrievedTime: Date? = null,
                      override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
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
                     override val error: Throwable? = null) : BoardItemInfo {

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