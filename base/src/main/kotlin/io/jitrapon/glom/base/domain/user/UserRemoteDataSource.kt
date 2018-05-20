package io.jitrapon.glom.base.domain.user

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Flowable
import io.reactivex.Single

class UserRemoteDataSource : RemoteDataSource(), UserDataSource {

    private val api = retrofit.create(UserApi::class.java)

    override fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> {
        return api.getUsers().deserialize()
    }

    override fun getUser(userId: String): Single<User> {
        throw NotImplementedError()
    }

    override fun getCurrentUser(): Single<User> {
        throw NotImplementedError()
    }

    //region deserializer

    private fun Flowable<UsersResponse>.deserialize(): Flowable<List<User>> {

    }

    //endregion
}