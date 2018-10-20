package io.jitrapon.glom.base.domain.user.account

import android.accounts.AccountManager
import io.reactivex.Flowable

const val ACCOUNT_MANAGER_USER_ID = "user_id"
const val ACCOUNT_MANAGER_REFRESH_TOKEN = "refresh_token"
const val ACCOUNT_MANAGER_ID_TOKEN = "id_token"
const val ACCOUNT_MANAGER_ID_TOKEN_EXPIRED_IN = "expires_in"

class AccountLocalDataSource(private val accountManager: AccountManager) : AccountDataSource {

    /* in-memory cache for the currently signed in user, must be initialized
    from account manager as soon as it's available */
//    private var inMemoryAccount: AccountInfo? = null

    private var inMemoryAccount: AccountInfo? = AccountInfo(
            "okpkcLsh2xURIVfRZ1aWTKCmIcI3",
            "AGdpqewo8EkQXlgCZuNQyRB5iPgPiPlZ_Bd2WPV6JCds8vAdZyrhDBz0woEXQ" +
                    "5aBjm8Ur6JpAu3q3frfS1CF1b_-CfIKB1zbP7JWpyCq-g-cUwYIc2vvdsYUdV_KO4" +
                    "u5SYFZI3Jqk4PJLim3WBw_50v7apYx9BUNyHneg-D_KtgDleZMXOJNdyyuM0SO2kL" +
                    "Q_5X_2Vrjn6ka6YPCVIzauhVrRfDezAkJwA",
            "abcd1234",
            3600L
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
                                manager.getUserData(it, ACCOUNT_MANAGER_ID_TOKEN),
                                manager.getUserData(it, ACCOUNT_MANAGER_ID_TOKEN_EXPIRED_IN).toLong())
                        inMemoryAccount
                    }
                }
            }
        }
        else inMemoryAccount
    }

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return Flowable.just(inMemoryAccount)
    }

    override fun saveAccount(account: AccountInfo): Flowable<AccountInfo> {
        return Flowable.fromCallable {
            // save account info to in-memory cache and one in AccountManager
            createOrUpdateAccountInAccountManager(account)

            inMemoryAccount = account
            inMemoryAccount
        }
    }

    private fun createOrUpdateAccountInAccountManager(account: AccountInfo) {
//        accountManager.getAccountsByType()
    }
}
