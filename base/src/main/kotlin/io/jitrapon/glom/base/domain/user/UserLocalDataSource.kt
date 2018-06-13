package io.jitrapon.glom.base.domain.user

import android.support.v4.util.ArrayMap
import io.jitrapon.glom.base.domain.BaseDatabase
import io.reactivex.Flowable

class UserLocalDataSource(database: BaseDatabase) : UserDataSource {

    private var inMemoryUsers: ArrayMap<String, User> = ArrayMap()

    override fun getUsers(circleId: String, refresh: Boolean): Flowable<List<User>> {

    }

    override fun getUsers(userIds: List<String>): Flowable<List<User>> {

    }
}
