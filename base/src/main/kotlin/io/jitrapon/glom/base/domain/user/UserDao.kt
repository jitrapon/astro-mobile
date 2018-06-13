package io.jitrapon.glom.base.domain.user

import android.arch.persistence.room.*

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id IN (SELECT user_id FROM user_circle WHERE circle_id = :circleId)")
    fun getUsersInCircle(circleId: String)

    @Query("DELETE FROM user_circle WHERE circle_id = :circleId")
    fun deleteUsersInCircle(circleId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: UserCircleEntity)

    @Transaction
    fun deleteAndInsertUserInCircle(circleId: String, vararg userIds: String) {
        deleteUsersInCircle(circleId)
        for (userId in userIds) {
            insert(UserCircleEntity(userId, circleId))
        }
    }
}
