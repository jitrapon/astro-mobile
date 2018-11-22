package io.jitrapon.glom.base.domain.user.account

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Completable
import io.reactivex.Flowable

class AccountRemoteDataSource : RemoteDataSource(), AccountDataSource {

    private val api = retrofit.create(AccountApi::class.java)

    override fun initAccount(): Completable {
        throw NotImplementedError()
    }

    override fun getAccount(): AccountInfo? {
        throw NotImplementedError()
    }

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return api.refreshIdToken("http://192.168.1.35:8081/token", RefreshIdTokenRequest(refreshToken)).map {
            AccountInfo(it.userId, it.refreshToken, it.idToken, it.expireTime)
        }
    }

    override fun saveAccount(account: AccountInfo): Flowable<AccountInfo> {
        throw NotImplementedError()
    }

    override fun signInWithEmailPassword(email: CharArray, password: CharArray): Flowable<AccountInfo> {
        return api.signInWithEmailPassword("http://192.168.1.35:8081/auth/signin?type=password",
            SignInEmailPasswordRequest(String(email), String(password))).map {
            AccountInfo(it.userId, it.refreshToken, it.idToken, it.expireTime)
        }
    }
}
