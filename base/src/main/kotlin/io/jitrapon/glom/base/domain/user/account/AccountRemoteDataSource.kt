package io.jitrapon.glom.base.domain.user.account

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Completable
import io.reactivex.Flowable

class AccountRemoteDataSource : RemoteDataSource(), AccountDataSource {

    private val api = retrofit.create(AccountApi::class.java)

    override fun getAccount(): AccountInfo? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun saveAccount(userId: String, idToken: String?): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return api.refreshIdToken("http://192.168.1.35:8081/token", RefreshIdTokenRequest(refreshToken)).map {
            AccountInfo(it.userId, it.refreshToken, it.idToken)
        }
    }

    override fun updateAccount(account: AccountInfo): Flowable<AccountInfo> {
        throw NotImplementedError()
    }
}