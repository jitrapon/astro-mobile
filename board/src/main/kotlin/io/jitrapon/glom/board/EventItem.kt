package io.jitrapon.glom.board

import android.os.Parcel
import android.os.Parcelable
import java.util.*

/**
 * Represents an Event in the board
 *
 * @author Jitrapon Tiachunpun
 */
data class EventItem(override val itemType: Int,
                     override val itemId: String,
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
