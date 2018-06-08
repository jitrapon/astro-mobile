package io.jitrapon.glom.board.event

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import io.reactivex.Flowable

@Dao
interface EventItemDao {

    @Query("SELECT * FROM events WHERE circle_id = :circleId")
    fun getEventsInCircle(circleId: String): Flowable<List<EventItemEntity>>
}