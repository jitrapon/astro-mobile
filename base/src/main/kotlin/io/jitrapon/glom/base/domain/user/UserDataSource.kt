package io.jitrapon.glom.base.domain.user

import io.reactivex.Flowable

/**
 * Main entry point for accessing user data
 *
 * @author Jitrapon Tiachunpun
 */
interface UserDataSource {

    fun getUsers(circleId: String, refresh: Boolean = false): Flowable<List<User>>

    fun getUsers(userIds: List<String>): Flowable<List<User>>
}
