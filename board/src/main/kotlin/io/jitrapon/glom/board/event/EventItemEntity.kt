package io.jitrapon.glom.board.event

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "events")
data class EventItemEntity(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
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
        @ColumnInfo(name = "note")
        val note: String?,
        @ColumnInfo(name = "time_zone")
        val timeZone: String?,
        @ColumnInfo(name = "is_full_day")
        val isFullDay: Boolean,
        @ColumnInfo(name = "is_repeating")
        val isRepeating: Boolean,
        @ColumnInfo(name = "date_poll_status")
        val datePollStatus: Boolean,
        @ColumnInfo(name = "place_poll_status")
        val placePollStatus: Boolean,
        @ColumnInfo(name = "circle_id")
        val circleId: String
)
