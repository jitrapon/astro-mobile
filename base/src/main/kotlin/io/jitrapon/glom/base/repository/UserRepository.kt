package io.jitrapon.glom.base.repository

import io.jitrapon.glom.base.model.User
import io.reactivex.Flowable

/**
 * Repository for retrieving and saving User information
 *
 * @author Jitrapon Tiachunpun
 */
class UserRepository : Repository<User>() {

    override fun load(): Flowable<User> {
        //not applicable
        throw NotImplementedError()
    }

    override fun loadList(): Flowable<List<User>> {
        return Flowable.just(ArrayList<User>().apply {
            add(User(User.TYPE_USER, "yoshi3003", "boat", null))
            add(User(User.TYPE_USER, "fatcat18", "nad", null))
            add(User(User.TYPE_USER, "fluffy", "fluffy", null))
            add(User(User.TYPE_USER, "panda", "panda", null))
        })
    }
}
