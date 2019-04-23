package io.jitrapon.glom.base.domain.user

import androidx.room.*
import io.reactivex.Single

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id IN (SELECT user_id FROM user_circle WHERE circle_id = :circleId)")
    fun getUsersInCircle(circleId: String): Single<List<UserEntity>>

    @Query("DELETE FROM user_circle WHERE circle_id = :circleId")
    fun deleteUsersInCircle(circleId: String)

    /**
     * SQLite does not support UPSERT yet. When a user entity has already been inserted before,
     * if the return value from INSERT operation with IGNORE equals to -1, then it means
     * the row was not inserted before. We don't want to put conflict strategy as REPLACE,
     * because it will trigger a DELETE and INSERT, and a delete operation would trigger a child
     * table DELETE cascade, which is an unwanted side effect.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: UserCircleEntity)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun update(entity: UserEntity)

    @Transaction
    fun replaceUsersInCircle(circleId: String, vararg users: UserEntity) {
        deleteUsersInCircle(circleId)
        for (user in users) {
            val rowId = insert(user)
            if (rowId == -1L) {
                update(user)
            }
            insert(UserCircleEntity(user.id, circleId))
        }
    }
}
