package io.jitrapon.glom.base.domain.user.account

import io.reactivex.Flowable

/**
 * An interface representing the functionality of CRUD with a user account
 *
 * Created by Jitrapon
 */
interface AccountDataSource {

    fun getAccount(): AccountInfo?

    fun refreshToken(refreshToken: String? = null): Flowable<AccountInfo>

    fun saveAccount(account: AccountInfo): Flowable<AccountInfo>
}

