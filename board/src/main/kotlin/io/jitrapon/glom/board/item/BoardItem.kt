package io.jitrapon.glom.board.item

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.board.item.event.EventItem

/**
 * A DataModel class representing a single board item
 *
 * @author Jitrapon Tiachunpun
 */
interface BoardItem : DataModel {

    val itemType: Int

    val itemId: String

    val createdTime: Long?

    val updatedTime: Long?

    val owners: List<String>

    var syncStatus: SyncStatus

    val itemInfo: BoardItemInfo

    val isEditable: Boolean

    companion object : Parcelable.Creator<BoardItem> {

        const val TYPE_EVENT = 1
        const val TYPE_FILE = 2
        const val TYPE_DRAWING = 3
        const val TYPE_LINK = 4
        const val TYPE_LIST = 5
        const val TYPE_NOTE = 6

        override fun newArray(size: Int): Array<BoardItem?> = arrayOfNulls(size)

        override fun createFromParcel(source: Parcel): BoardItem? = getSpecificItemType(source)

        /**
         * Creates a parcelable class based on the itemId
         */
        private fun getSpecificItemType(source: Parcel): BoardItem? {
            source.readInt().let {
                return when (it) {
                    TYPE_EVENT -> EventItem(source, it)
                    else -> null
                }
            }
        }
    }

    /**
     * Updates this item info
     */
    fun setInfo(info: BoardItemInfo)
}

/**
 * Status of whether or not the board item has been synced to the remote source successfully
 */
enum class SyncStatus(val intValue: Int) {
    SUCCESS(0),        // indicates that the item has synced successfully to the remote source
    FAILED(-1),        // indicates that the item has failed to sync due to an error and requires to be sync again if possible
    ACTIVE(1),         // indicates that the item is in the process of syncing with the remote source
    OFFLINE(2);        // indicates that the item is not meant to by synced with remote source

    companion object {
        internal val map = SyncStatus.values().associateBy(SyncStatus::intValue)
    }
}

fun Int.toSyncStatus(): SyncStatus = SyncStatus.map[this] ?: SyncStatus.OFFLINE
