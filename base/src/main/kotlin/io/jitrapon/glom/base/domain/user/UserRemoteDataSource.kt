package io.jitrapon.glom.base.domain.user

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Flowable
import java.util.*

class UserRemoteDataSource : RemoteDataSource(), UserDataSource {

    private val api = retrofit.create(UserApi::class.java)

    override fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> {
        return api.getUsers(circleId).map {
            it.deserialize()
        }
    }

    override fun getUsers(userIds: List<String>): Flowable<List<User>> {
        throw NotImplementedError()
    }

    override fun saveUsers(users: List<User>): Flowable<List<User>> {
        throw NotImplementedError()
    }

    override fun getCurrentUserId(): String? {
        throw NotImplementedError()
    }

    //region deserializer

    private fun UsersResponse.deserialize(): List<User> {
        val now = Date()
        return ArrayList<User>().apply {
            this@deserialize.users.forEach {
                add(User(it.userType, it.userId, it.userName, it.avatar, now))
            }
        }
    }

    //endregion
}