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

    override fun getAccount(): AccountInfo? = localDataSource.getAccount()

    override fun saveAccount(userId: String, idToken: String?): Completable {
        throw NotImplementedError()
    }

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return load(true,
                localDataSource.refreshToken(),
                remoteDataSource.refreshToken(localDataSource.getAccount()?.refreshToken),
                localDataSource::updateAccount)
    }

    override fun updateAccount(account: AccountInfo): Flowable<AccountInfo> {
        throw NotImplementedError()
    }
}