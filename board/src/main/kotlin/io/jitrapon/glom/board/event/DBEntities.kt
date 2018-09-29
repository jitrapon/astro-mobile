package io.jitrapon.glom.board.event

import androidx.room.*
import androidx.room.ForeignKey.CASCADE

@Entity(tableName = "events")
data class EventItemEntity(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "updated_time")
        val updatedTime: Long,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "start_time")
        val startTime: Long?,
        @ColumnInfo(name = "end_time")
        val endTime: Long?,
        @ColumnInfo(name = "g_place_id")
        val googlePlaceId: String?,
        @ColumnInfo(name = "place_id")
        val placeId: String?,
        @ColumnInfo(name = "latitude")
        val latitude: Double?,
        @ColumnInfo(name = "longitude")
        val longitude: Double?,
        @ColumnInfo(name = "place_name")
        val placeName: String?,
        @ColumnInfo(name = "place_description")
        val placeDescription: String?,
        @ColumnInfo(name = "place_address")
        val placeAddress: String?,
        @ColumnInfo(name = "note")
        val note: String?,
        @ColumnInfo(name = "time_zone")
        val timeZone: String?,
        @ColumnInfo(name = "is_full_day")
        val isFullDay: Boolean,
        @ColumnInfo(name = "date_poll_status")
        val datePollStatus: Boolean,
        @ColumnInfo(name = "place_poll_status")
        val placePollStatus: Boolean,
        @ColumnInfo(name = "is_owner")
        val isOwner: Boolean,
        @ColumnInfo(name = "circle_id")
        val circleId: String
)

@Entity(tableName = "event_attendees", primaryKeys = ["event_id", "user_id"], foreignKeys = [
    ForeignKey(entity = EventItemEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onUpdate = CASCADE, onDelete = CASCADE)
])
data class EventAttendeeEntity(
        @ColumnInfo(name = "event_id")
        val eventId: String,
        @ColumnInfo(name = "user_id")
        val userId: String
)

class EventItemFullEntity {

    @Embedded
    lateinit var entity: EventItemEntity

    @Relation(parentColumn = "id", entityColumn = "event_id", entity = EventAttendeeEntity::class, projection = ["user_id"])
    lateinit var attendees: List<String>
}


