package io.jitrapon.glom.base.domain.user

import androidx.collection.ArrayMap
import io.jitrapon.glom.base.domain.BaseDatabase
import io.reactivex.Flowable

class UserLocalDataSource(database: BaseDatabase) : UserDataSource {

    /* currently active circle in which users are in */
    private var circleId: String? = null
    private var inMemoryUsers: androidx.collection.ArrayMap<String, User> = androidx.collection.ArrayMap()

    /* DAO access object to users */
    private val userDao: UserDao = database.userDao()

    override fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> {
        // if this circle has been loaded before, just return the cached version
        return if (circleId == this.circleId) Flowable.just(inMemoryUsers.map { it.value })

        // otherwise invoke the item corresponding DAO
        else {
            userDao.getUsersInCircle(circleId).toFlowable()
                    .map { it.toUsers() }
                    .doOnNext {
                        this.circleId = circleId
                        cacheUsersInMemory(it)
                    }
        }
    }

    override fun saveUsers(users: List<User>): Flowable<List<User>> {
        return Flowable.fromCallable {
            userDao.replaceUsersInCircle(circleId!!, *users.map { UserEntity(it.userId, it.userName, it.avatar, it.userType) }.toTypedArray())
            users
        }.doOnNext {
            cacheUsersInMemory(it)
        }
    }

    override fun getUsers(userIds: List<String>): Flowable<List<User>> = Flowable.just(ArrayList<User>().apply {
        userIds.forEach {
            inMemoryUsers[it]?.let(::add)
        }
    })

    private fun List<UserEntity>.toUsers(): List<User> {
        return map { User(it.type, it.id, it.name, it.avatar) }
    }

    private fun cacheUsersInMemory(users: List<User>) {
        inMemoryUsers.clear()
        users.map {
            inMemoryUsers.put(it.userId, it)
        }
    }
}
