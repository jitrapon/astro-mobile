package io.jitrapon.glom.board.event

import android.arch.persistence.room.*
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

    @Query("DELETE FROM events WHERE id = :itemId")
    fun deleteEventById(itemId: String)
}