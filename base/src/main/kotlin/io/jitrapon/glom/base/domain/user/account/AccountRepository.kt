package io.jitrapon.glom.base.domain.user.account

import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Created by Jitrapon
 */
class AccountRepository(private val localDataSource: AccountDataSource,
                        private val remoteDataSource: AccountDataSource)
    : Repository<AccountInfo>(), AccountDataSource {

    override fun initAccount(): Completable {
        return localDataSource.initAccount()
    }

    override fun getAccount(): AccountInfo? = localDataSource.getAccount()

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return load(true,
                localDataSource.refreshToken(),
                remoteDataSource.refreshToken(localDataSource.getAccount()?.refreshToken),
                localDataSource::saveAccount)
    }

    override fun saveAccount(account: AccountInfo): Flowable<AccountInfo> {
        return localDataSource.saveAccount(account)
    }

    override fun signInWithEmailPassword(email: CharArray, password: CharArray): Flowable<AccountInfo> {
        return load(true,
            localDataSource.signInWithEmailPassword(email, password),
            remoteDataSource.signInWithEmailPassword(email, password),
            localDataSource::saveAccount)
    }

    override fun signUpAnonymously(): Flowable<AccountInfo> {
        return load(true,
                localDataSource.signUpAnonymously(),
                remoteDataSource.signUpAnonymously(),
                localDataSource::saveAccount)
    }

    override fun signUpWithEmailPassword(email: CharArray, password: CharArray, idToken: String?): Flowable<AccountInfo> {
        return load(true,
            localDataSource.signUpWithEmailPassword(email, password, idToken),
            remoteDataSource.signUpWithEmailPassword(email, password, idToken),
            localDataSource::saveAccount)
    }

    override fun signInWithOAuthCredential(token: String, provider: String): Flowable<OAuthAccountInfo> {
        return remoteDataSource.signInWithOAuthCredential(token, provider)
            .map {
                localDataSource.saveAccount(AccountInfo(it.userId, it.refreshToken, it.idToken, it.expiresIn, false)).blockingFirst()
                it
            }
    }

    override fun signOut(): Completable {
        return delete(localDataSource.signOut(), remoteDataSource.signOut(), true)
    }
}
