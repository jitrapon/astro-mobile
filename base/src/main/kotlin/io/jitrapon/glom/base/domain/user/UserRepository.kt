package io.jitrapon.glom.base.domain.user

import android.support.v4.util.ArrayMap
import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Flowable

/**
 * Repository for retrieving and saving User information
 *
 * @author Jitrapon Tiachunpun
 */
class UserRepository(private val remoteDataSource: UserDataSource, private val localDataSource: UserDataSource) :
        Repository<User>(), UserDataSource {

    override fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> {
        return loadList(refresh,
                Flowable.just(users),
                remoteDataSource.getUsers(circleId, refresh),
                {
                    users = it
                    userMap = userMap.apply {
                        users?.map {
                            put(it.userId, it)
                        }
                    }
                    users!!
                }
        )
    }

    override fun getUsers(userIds: List<String>): Flowable<List<User>> {
        return Flowable.just(ArrayList<User>().apply {
            userIds.forEach {
                userMap[it]?.let(::add)
            }
        })
    }
}
