package io.jitrapon.glom.board.item.event

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.BoardItemInfo
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.toSyncStatus
import java.util.*

/**
 * Represents an Event in the board
 *
 * @author Jitrapon Tiachunpun
 */
data class EventItem(override val itemType: Int,
                     override var itemId: String,
                     override val createdTime: Long?,
                     override var updatedTime: Long?,
                     override val owners: List<String>,
                     override var itemInfo: EventInfo,
                     override val isEditable: Boolean,
                     override var syncStatus: SyncStatus = SyncStatus.OFFLINE,
                     override var retrievedTime: Date? = Date(),
                     override val error: Throwable? = null) : BoardItem {

    override fun setInfo(info: BoardItemInfo) {
        updatedTime = Date().time
        itemInfo = info as EventInfo
    }

    constructor(parcel: Parcel, type: Int) : this(
            type,
            parcel.readString()!!,
            parcel.readLong().let {
                if (it == -1L) null
                else it
            },
            parcel.readLong().let {
                if (it == -1L) null
                else it
            },
            parcel.createStringArrayList()!!,
            parcel.readParcelable(EventInfo::class.java.classLoader)!!,
            parcel.readInt() == 1,
            parcel.readInt().toSyncStatus(),
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
        parcel.writeInt(if (isEditable) 1 else 0)
        parcel.writeInt(syncStatus.intValue)
        parcel.writeLong(retrievedTime?.time ?: -1L)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EventItem> {
        override fun createFromParcel(parcel: Parcel): EventItem = EventItem(parcel, parcel.readInt())

        override fun newArray(size: Int): Array<EventItem?> = arrayOfNulls(size)
    }

    override val isSyncable: Boolean
        get() = itemInfo.source.isBoard() || itemInfo.newSource?.isBoard() == true

    override val isSyncingToRemote: Boolean
        get() = !itemInfo.source.isBoard() && itemInfo.newSource?.isBoard() == true

    override val isSyncingToLocal: Boolean
        get() = itemInfo.source.isBoard() && itemInfo.newSource?.isBoard()?.not() == true

    val instanceEventId: String
        get() = itemId.substringBefore(".")
}
