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

    @Throws(NoRefreshTokenException::class)
    fun refreshToken(refreshToken: String? = null): Flowable<AccountInfo>

    fun saveAccount(account: AccountInfo): Flowable<AccountInfo>

    fun signInWithEmailPassword(email: CharArray, password: CharArray): Flowable<AccountInfo>

    fun signUpAnonymously(): Flowable<AccountInfo>

    fun signUpWithEmailPassword(email: CharArray, password: CharArray, idToken: String?): Flowable<AccountInfo>

    fun signOut(): Completable

    fun signInWithOAuthCredential(token: String): Flowable<OAuthAccountInfo>
}

class NoRefreshTokenException : Exception()

