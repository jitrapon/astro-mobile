package io.jitrapon.glom.base.domain.user

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.ForeignKey.CASCADE
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "avatar")
        val avatar: String?,
        @ColumnInfo(name = "type")
        val type: Int
)

@Entity(tableName = "user_circle", primaryKeys = ["user_id", "circle_id"], foreignKeys = [
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onUpdate = CASCADE, onDelete = CASCADE)
])
data class UserCircleEntity(
        @ColumnInfo(name = "user_id")
        val userId: String,
        @ColumnInfo(name = "circle_id")
        val circleId: String
)