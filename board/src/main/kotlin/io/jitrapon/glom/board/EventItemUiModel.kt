package io.jitrapon.glom.board

import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.board.BoardItemUiModel.Companion.TYPE_EVENT

/**
 * @author Jitrapon Tiachunpun
 */
data class EventItemUiModel(override val itemId: String?,
                            var title: String,
                            var dateTime: String?,
                            var location: AndroidString?,
                            var mapLatLng: LatLng?,     // if not null, will show mini map at the specified lat lng
                            var attendeesAvatars: MutableList<String?>?,
                            var attendStatus: AttendStatus,
                            override val itemType: Int = TYPE_EVENT,
                            override var status: UiModel.Status = UiModel.Status.SUCCESS) : BoardItemUiModel {

    companion object {

        const val TITLE = 0
        const val DATETIME = 1
        const val LOCATION = 2
        const val MAPLATLNG = 3
        const val ATTENDEES = 4
        const val ATTENDSTATUS = 5
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
            if (attendStatus != otherItem.attendStatus) add(ATTENDSTATUS)
        }
    }

    /**
     * Status of attending the event of this current user
     */
    enum class AttendStatus {
        DECLINED, MAYBE, GOING;
    }
}