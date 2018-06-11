package io.jitrapon.glom.board.event

import android.arch.persistence.room.*
import io.reactivex.Single

@Dao
interface EventItemDao {

    @Query("SELECT * FROM events WHERE circle_id = :circleId")
    fun getEventsInCircle(circleId: String): Single<List<EventItemFullEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(event: EventItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(attendees: List<EventAttendeeEntity>)

    @Transaction
    fun insertOrReplaceEvents(events: List<EventItemFullEntity>) {
        for (event in events) {
            insert(event.entity)        // when a replace occurs, SQLite will trigger a cascade delete to child tables as well
            insert(event.attendees.map { EventAttendeeEntity(event.entity.id, it) })
        }
    }
}