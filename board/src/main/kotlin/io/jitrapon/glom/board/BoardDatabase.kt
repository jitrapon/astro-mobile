package io.jitrapon.glom.board

import androidx.room.Database
import androidx.room.RoomDatabase
import io.jitrapon.glom.board.item.event.CalendarEntity
import io.jitrapon.glom.board.item.event.EventAttendeeEntity
import io.jitrapon.glom.board.item.event.EventItemDao
import io.jitrapon.glom.board.item.event.EventItemEntity

@Database(entities = [EventItemEntity::class, EventAttendeeEntity::class, CalendarEntity::class], version = 1, exportSchema = false)
abstract class BoardDatabase : RoomDatabase() {

    abstract fun eventItemDao(): EventItemDao

    abstract fun eventItemPreferenceDao(): EventItemPreferenceDao
}