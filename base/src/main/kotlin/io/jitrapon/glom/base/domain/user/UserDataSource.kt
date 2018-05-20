package io.jitrapon.glom.base.domain.user

import io.reactivex.Flowable
import io.reactivex.Single

/**
 * Main entry point for accessing user data
 *
 * @author Jitrapon Tiachunpun
 */
interface UserDataSource {

    fun getUsers(circleId: String, refresh: Boolean = false): Flowable<List<User>>

    fun getUser(userId: String): Single<User>
}
