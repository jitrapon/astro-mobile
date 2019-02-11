package io.jitrapon.glom.board

import androidx.room.*
import io.jitrapon.glom.board.item.event.CalendarEntity
import io.reactivex.Single

@Dao
interface EventItemPreferenceDao {

    @Query("SELECT * FROM calendars WHERE circle_id = :circleId")
    fun getSyncedCalendars(circleId: String): Single<List<CalendarEntity>>

    @Transaction
    fun insertOrReplaceCalendars(calendars: List<CalendarEntity>) {
        for (calendar in calendars) {
            insertCalendar(calendar)
        }
    }

    @Transaction
    fun deleteCalendars(idList: List<String>) {
        for (id in idList) {
            deleteCalendarById(id)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCalendar(calendar: CalendarEntity)

    @Query("DELETE FROM calendars WHERE calendar_id = :calId")
    fun deleteCalendarById(calId: String)
}
