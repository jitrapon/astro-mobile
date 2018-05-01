package io.jitrapon.glom.base.domain.user

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Flowable
import io.reactivex.Single
import java.util.*

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

    override fun getTestUsers(): Flowable<List<User>> {
        val now = Date()
        return api.getTestUsers().map { it.map { it.toUser(now) } }
    }

    private fun UserResponse.toUser(retrievedTime: Date): User = User(1, id, name, null, retrievedTime)
}