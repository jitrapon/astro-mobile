package io.jitrapon.glom.base.domain

import io.jitrapon.glom.base.model.User
import io.reactivex.Flowable

/**
 * Main entry point for accessing user data
 *
 * @author Jitrapon Tiachunpun
 */
interface UserDataSource {

    fun getUsers(circleId: String): Flowable<List<User>>

    fun getUser(userId: String): Flowable<User?>

    fun getCurrentUser(): Flowable<User?>
}
