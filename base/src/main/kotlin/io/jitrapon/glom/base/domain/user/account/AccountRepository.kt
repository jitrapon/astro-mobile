package io.jitrapon.glom.base.domain.user.account

import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Completable

/**
 * Created by Jitrapon
 */
class AccountRepository(private val localDataSource: AccountDataSource,
                        private val remoteDataSource: AccountDataSource)
    : Repository<AccountInfo>(), AccountDataSource {

    override fun getAccount(): AccountInfo? = localDataSource.getAccount()

    override fun saveAccount(userId: String, idToken: String?): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}