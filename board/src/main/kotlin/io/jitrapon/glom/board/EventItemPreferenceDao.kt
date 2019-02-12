package io.jitrapon.glom.board

import androidx.room.*
import io.jitrapon.glom.board.item.event.CalendarEntity
import io.reactivex.Single

@Dao
interface EventItemPreferenceDao {

    @Query("SELECT * FROM calendars WHERE circle_id = :circleId")
    fun getSyncedCalendars(circleId: String): Single<List<CalendarEntity>>

    @Transaction
    fun insertAndRemoveCalendars(insertList: List<CalendarEntity>, removeList: List<String>) {
        for (calendar in insertList) {
            insertCalendar(calendar)
        }
        for (id in removeList) {
            deleteCalendarById(id)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCalendar(calendar: CalendarEntity)

    @Query("DELETE FROM calendars WHERE calendar_id = :calId")
    fun deleteCalendarById(calId: String)
}
