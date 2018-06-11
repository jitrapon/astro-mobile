package io.jitrapon.glom.board

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import io.jitrapon.glom.board.event.EventAttendeeEntity
import io.jitrapon.glom.board.event.EventItemDao
import io.jitrapon.glom.board.event.EventItemEntity

@Database(entities = [EventItemEntity::class, EventAttendeeEntity::class], version = 1, exportSchema = false)
abstract class BoardDatabase : RoomDatabase() {

    abstract fun eventItemDao(): EventItemDao
}