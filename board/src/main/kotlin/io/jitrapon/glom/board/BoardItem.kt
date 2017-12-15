package io.jitrapon.glom.board

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel

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

    val itemInfo: BoardItemInfo

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
}