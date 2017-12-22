package io.jitrapon.glom.board

import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.board.BoardItemUiModel.Companion.TYPE_EVENT

/**
 * @author Jitrapon Tiachunpun
 */
data class EventItemUiModel(override val itemId: String?,
                            val title: String,
                            val dateTime: String?,
                            var location: AndroidString?,
                            val mapLatLng: LatLng?,     // if not null, will show mini map at the specified lat lng
                            val attendeesAvatars: List<String?>?,
                            override val itemType: Int = TYPE_EVENT) : BoardItemUiModel {

    companion object {

        const val TITLE = 0
        const val DATETIME = 1
        const val LOCATION = 2
        const val MAPLATLNG = 3
        const val ATTENDEES = 4
    }

    override fun getChangePayload(other: BoardItemUiModel?): List<Int> {
        if (other == null) return ArrayList()

        val otherItem = other as EventItemUiModel
        return ArrayList<Int>().apply {
            if (title != otherItem.title) add(TITLE)
            if (dateTime != otherItem.dateTime) add(DATETIME)
            if (location != otherItem.location) add(LOCATION)
            if (mapLatLng != otherItem.mapLatLng) add(MAPLATLNG)
            if (attendeesAvatars != otherItem.attendeesAvatars) add(ATTENDEES)
        }
    }
}