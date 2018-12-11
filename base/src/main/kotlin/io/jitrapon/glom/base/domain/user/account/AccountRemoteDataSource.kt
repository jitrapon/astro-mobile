package io.jitrapon.glom.base.domain.user.account

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Completable
import io.reactivex.Flowable

class AccountRemoteDataSource : RemoteDataSource(), AccountDataSource {

    private val api = retrofit.create(AccountApi::class.java)

    private val mockApi: String = "http://www.mocky.io/v2/5c0630543300006f00e814b5"

    override fun initAccount(): Completable {
        throw NotImplementedError()
    }

    override fun getAccount(): AccountInfo? {
        throw NotImplementedError()
    }

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        refreshToken ?: throw NoRefreshTokenException()

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
            AccountInfo(it.userId, it.refreshToken, it.idToken, it.expireTime, false)
        }
    }

    override fun signUpAnonymously(): Flowable<AccountInfo> {
        return api.signUpAnonymously("http://192.168.1.35:8081/auth/signup?type=anonymous").map {
            AccountInfo(it.userId, it.refreshToken, it.idToken, it.expireTime, true)
        }
    }

    override fun signUpWithEmailPassword(email: CharArray, password: CharArray, idToken: String?): Flowable<AccountInfo> {
        return api.signUpWithEmailPassword("http://192.168.1.35:8081/auth/signup?type=password",
            SignUpEmailPasswordRequest(String(email), String(password), idToken)).map {
            AccountInfo(it.userId, it.refreshToken, it.idToken, it.expireTime, false)
        }
    }

    override fun signOut(): Completable {
        return api.signOut("http://192.168.1.35:8081/token/revoke")
    }
}
