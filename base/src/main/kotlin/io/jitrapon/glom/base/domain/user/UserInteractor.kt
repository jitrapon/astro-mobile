package io.jitrapon.glom.base.domain.user

import io.jitrapon.glom.base.util.AppLogger
import io.reactivex.Flowable
import java.util.*

/**
 * Interactor dealing with Users
 *
 * @author Jitrapon Tiachunpun
 */
class UserInteractor(private val dataSource: UserDataSource) {

    private var signedInUserId: String? = "yoshi3003"

    fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> = dataSource.getUsers(circleId, refresh)

    fun getCurrentUserId(): String? = signedInUserId

    fun getUsersFromIds(userIds: List<String>): List<User?>? {
        return ArrayList<User?>().apply {
            userIds.forEach {
                try {
                    add(dataSource.getUser(it).blockingGet())
                }
                catch (ex: Exception) {
                    AppLogger.e(ex)
                }
            }
        }
    }
}
