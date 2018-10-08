package io.jitrapon.glom.base.domain.user.account

import io.reactivex.Completable

/**
 * An interface representing the functionality of CRUD with a user account
 *
 * Created by Jitrapon
 */
interface AccountDataSource {

    fun getAccount(): AccountInfo?

    fun saveAccount(userId: String, idToken: String?): Completable
}

