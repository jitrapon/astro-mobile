package io.jitrapon.glom.base.domain.user

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Flowable
import io.reactivex.Single

class UserRemoteDataSource : RemoteDataSource(), UserDataSource {

    private val api = retrofit.create(UserApi::class.java)

    override fun getUsers(circleId: String): Flowable<List<User>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUser(userId: String): Single<User> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCurrentUser(): Single<User> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}