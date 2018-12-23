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

    /**
     * Creates a new account with the specified email and password. Optionally specify an idToken
     * from an anonymous session to link it with the newly created account
     */
    fun signUpWithEmailPassword(email: CharArray, password: CharArray, idToken: String?): Flowable<AccountInfo>

    fun signOut(): Completable

    /**
     * Creates a new account with the specified access or id token from an oauth provider. Optionally specify an idToken
     * from an anonymous session to link it with the newly created account
     */
    fun signInWithOAuthCredential(token: String, provider: String, idToken: String?): Flowable<OAuthAccountInfo>
}

class NoRefreshTokenException : Exception()

