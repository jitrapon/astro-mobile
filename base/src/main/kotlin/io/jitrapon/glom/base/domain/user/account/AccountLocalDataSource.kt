package io.jitrapon.glom.base.domain.user.account

import android.accounts.AccountManager
import com.facebook.appevents.AppEventsLogger.getUserData
import io.reactivex.Completable
import io.reactivex.Flowable

const val ACCOUNT_MANAGER_USER_ID = "user_id"
const val ACCOUNT_MANAGER_REFRESH_TOKEN = "refresh_token"
const val ACCOUNT_MANAGER_ID_TOKEN = "id_token"

class AccountLocalDataSource(private val accountManager: AccountManager) : AccountDataSource {

    /* in-memory cache for the currently signed in user, must be initialized
    from account manager as soon as it's available */
    private var inMemoryAccount: AccountInfo? = AccountInfo(
            "okpkcLsh2xURIVfRZ1aWTKCmIcI3",
            "AGdpqewo8EkQXlgCZuNQyRB5iPgPiPlZ_Bd2WPV6JCds8vAdZyrhDBz0woEXQ" +
                    "5aBjm8Ur6JpAu3q3frfS1CF1b_-CfIKB1zbP7JWpyCq-g-cUwYIc2vvdsYUdV_KO4" +
                    "u5SYFZI3Jqk4PJLim3WBw_50v7apYx9BUNyHneg-D_KtgDleZMXOJNdyyuM0SO2kL" +
                    "Q_5X_2Vrjn6ka6YPCVIzauhVrRfDezAkJwA",
            "abcd1234"
    )

    override fun getAccount(): AccountInfo? {
        return if (inMemoryAccount == null) {
            accountManager.getAccountsByType(null).firstOrNull().let {
                if (it == null) null
                else {
                    accountManager.let { manager ->
                        inMemoryAccount = AccountInfo(
                            manager.getUserData(it, ACCOUNT_MANAGER_USER_ID),
                            manager.getUserData(it, ACCOUNT_MANAGER_REFRESH_TOKEN),
                            manager.getUserData(it, ACCOUNT_MANAGER_ID_TOKEN))
                        inMemoryAccount
                    }
                }
            }
        }
        else inMemoryAccount
    }

    override fun saveAccount(userId: String, idToken: String?): Completable {
        throw NotImplementedError()
    }

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return Flowable.just(inMemoryAccount)
    }

    override fun updateAccount(account: AccountInfo): Flowable<AccountInfo> {
        return Flowable.fromCallable {
            inMemoryAccount = account
            inMemoryAccount
        }
    }
}
