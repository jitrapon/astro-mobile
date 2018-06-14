package io.jitrapon.glom.base.domain

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import io.jitrapon.glom.base.domain.user.UserCircleEntity
import io.jitrapon.glom.base.domain.user.UserDao
import io.jitrapon.glom.base.domain.user.UserEntity

@Database(entities = [UserEntity::class, UserCircleEntity::class], version = 1, exportSchema = false)
abstract class BaseDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
}
