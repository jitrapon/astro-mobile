package io.jitrapon.glom.base.domain.user

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
                localDataSource.getUsers(circleId, refresh),
                remoteDataSource.getUsers(circleId, refresh),
                localDataSource::saveUsers
        )
    }

    override fun getUsers(userIds: List<String>): Flowable<List<User>> {
        return localDataSource.getUsers(userIds)
    }

    override fun saveUsers(users: List<User>): Flowable<List<User>> {
        throw NotImplementedError()
    }
}
