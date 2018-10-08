package io.jitrapon.glom.base.domain.user

import io.jitrapon.glom.base.interactor.BaseInteractor
import io.reactivex.Flowable
import java.util.*

/**
 * Interactor dealing with Users and the currently signed in user
 *
 * @author Jitrapon Tiachunpun
 */
class UserInteractor(private val dataSource: UserDataSource): BaseInteractor() {

    fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> = dataSource.getUsers(circleId, refresh)

    fun getCurrentUserId(): String? = accountInteractor.getUserId()

    fun getUsersFromIds(userIds: List<String>): List<User?>? {
        return ArrayList<User?>().apply {
            dataSource.getUsers(userIds).blockingFirst().map {
                add(it)
            }
        }
    }
}
