package io.jitrapon.glom.base.repository

import io.jitrapon.glom.base.model.User
import io.reactivex.Flowable
import java.util.concurrent.TimeUnit

/**
 * Repository for retrieving and saving User information
 *
 * @author Jitrapon Tiachunpun
 */
class UserRepository : Repository<User>() {

    private var users: List<User>? = null

    override fun load(): Flowable<User> {
        //not applicable
        throw NotImplementedError()
    }

    override fun loadList(): Flowable<List<User>> {
        users = users ?: getItems()
        return Flowable.just(users!!).delay(500L, TimeUnit.MILLISECONDS)
    }

    fun getCache(): List<User>? = users

    private fun getItems(): List<User> {
        return ArrayList<User>().apply {
            add(User(User.TYPE_USER, "yoshi3003", "boat", null))
            add(User(User.TYPE_USER, "fatcat18", "nad", null))
            add(User(User.TYPE_USER, "fluffy", "fluffy", null))
            add(User(User.TYPE_USER, "panda", "panda", null))
        }
    }
}
