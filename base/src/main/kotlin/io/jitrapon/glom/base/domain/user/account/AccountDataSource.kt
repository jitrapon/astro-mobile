package io.jitrapon.glom.base.domain.user.account

import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * An interface representing the functionality of CRUD with a user account
 *
 * Created by Jitrapon
 */
interface AccountDataSource {

    fun getAccount(): AccountInfo?

    fun saveAccount(userId: String, idToken: String?): Completable

    fun refreshToken(refreshToken: String? = null): Flowable<AccountInfo>

    fun updateAccount(account: AccountInfo): Flowable<AccountInfo>
}

