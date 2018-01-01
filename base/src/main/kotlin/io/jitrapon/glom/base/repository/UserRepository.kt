package io.jitrapon.glom.base.repository

import android.support.v4.util.ArrayMap
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
    private var userMap: ArrayMap<String, User>? = null

    override fun load(): Flowable<User> {
        //not applicable
        throw NotImplementedError()
    }

    override fun loadList(): Flowable<List<User>> {
        users = users ?: getItems()
        return Flowable.just(users!!)
                .doOnEach {
                    it.value?.let { users ->
                        userMap = ArrayMap<String, User>().apply {
                            users.forEach { put(it.userId, it) }
                        }
                    }
                }
                .delay(500L, TimeUnit.MILLISECONDS)
    }

    fun getById(userId: String): User? = userMap?.get(userId)

    private fun getItems(): List<User> {
        return ArrayList<User>().apply {
            add(User(User.TYPE_USER, "yoshi3003", "boat", "https://media.licdn.com/mpr/mpr/shrinknp_200_200/AAEAAQAAAAAAAAUbAAAAJDY5ZDhhMDhhLTFkNDEtNDU5Ni1hNzEzLTVlNDhlZTlkNzg3ZA.jpg"))
            add(User(User.TYPE_USER, "fatcat18", "nad", "https://media.licdn.com/mpr/mpr/shrinknp_200_200/p/8/005/04b/03b/0f142fa.jpg"))
            add(User(User.TYPE_USER, "fluffy", "fluffy", "https://rodtank.files.wordpress.com/2015/06/05f26a_3bd838d073dd4c3e9519cd2f09d07fb6_srz_p_465_333_75_22_0-50_1-20_0-00_jpg_srz.jpg?w=382&h=274"))
            add(User(User.TYPE_USER, "panda", "panda", null))
        }
    }

    fun getCurrentUser(): User? = getById("yoshi3003")
}
