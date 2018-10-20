package io.jitrapon.glom.board.item.event

import androidx.room.*
import io.reactivex.Single

@Dao
interface EventItemDao {

    @Transaction
    @Query("SELECT * FROM events WHERE circle_id = :circleId")
    fun getEventsInCircle(circleId: String): Single<List<EventItemFullEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(event: EventItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAttendees(attendees: List<EventAttendeeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAttendee(attendee: EventAttendeeEntity)

    @Delete
    fun deleteAttendee(attendee: EventAttendeeEntity)

    @Transaction
    fun insertOrReplaceEvents(vararg events: EventItemFullEntity) {
        for (event in events) {
            insert(event.entity)        // when a replace occurs, SQLite will trigger a cascade delete to child tables as well
            insertAttendees(event.attendees.map { EventAttendeeEntity(event.entity.id, it) })
        }
    }

    @Transaction
    fun deleteEvents(vararg events: EventItemFullEntity) {
        for (event in events) {
            deleteEventById(event.entity.id)
        }
    }

    @Query("DELETE FROM events WHERE id = :itemId")
    fun deleteEventById(itemId: String)

    @Query("UPDATE events SET date_poll_status = :open WHERE id = :itemId")
    fun updateDatePollStatus(itemId: String, open: Boolean)

    @Query("UPDATE events SET start_time = :start, end_time = :end WHERE id = :itemId")
    fun updateDateTime(itemId: String, start: Long?, end: Long?)

    @Query("UPDATE events SET place_poll_status = :open WHERE id = :itemId")
    fun updatePlacePollStatus(itemId: String, open: Boolean)

    @Query("UPDATE events SET g_place_id = :googlePlaceId, place_id = :placeId, latitude = :latitude, longitude = :longitude, place_name = :placeName, place_description = :placeDescription, place_address = :placeAddress WHERE id = :itemId")
    fun updatePlace(itemId: String, googlePlaceId: String? = null, placeId: String? = null, latitude: Double? = null, longitude: Double? = null, placeName: String? = null,
                    placeDescription: String? = null, placeAddress: String? = null)
}