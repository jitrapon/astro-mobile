package io.jitrapon.glom.base.domain.user.account

import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * An interface representing the functionality of CRUD with a user account
 *
 * Created by Jitrapon
 */
interface AccountDataSource {
    
    fun initAccount(): Completable

    fun getAccount(): AccountInfo?

    fun refreshToken(refreshToken: String? = null): Flowable<AccountInfo>

    fun saveAccount(account: AccountInfo): Flowable<AccountInfo>

    fun signInWithEmailPassword(email: CharArray, password: CharArray): Flowable<AccountInfo>

    fun signOut(): Completable
}

class InvalidRefreshTokenException : Exception("Refresh token is invalid or missing")

