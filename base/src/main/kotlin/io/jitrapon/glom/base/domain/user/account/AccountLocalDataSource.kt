package io.jitrapon.glom.base.domain.user.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.os.Bundle
import io.jitrapon.glom.R
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.concurrent.atomic.AtomicBoolean

const val ACCOUNT_MANAGER_USER_ID = "user_id"
const val ACCOUNT_MANAGER_REFRESH_TOKEN = "refresh_token"
const val ACCOUNT_MANAGER_ID_TOKEN = "id_token"
const val ACCOUNT_MANAGER_ID_TOKEN_EXPIRED_IN = "expires_in"
const val ACCOUNT_MANAGER_IS_ANONYMOUS = "is_anonymous"

class AccountLocalDataSource(private val accountManager: AccountManager, private val application: Application)
    : AccountDataSource, OnAccountsUpdateListener {

    /* in-memory cache for the currently signed in user, must be initialized
    from account manager as soon as it's available */
    private var inMemoryAccount: AccountInfo? = null

    private val accountType: String
        get() = application.getString(R.string.account_manager_account_type)

    private val isInitialized = AtomicBoolean(false)

    companion object {

        val debugAccount: AccountInfo = AccountInfo(
                "okpkcLsh2xURIVfRZ1aWTKCmIcI3",
                "AGdpqewo8EkQXlgCZuNQyRB5iPgPiPlZ_Bd2WPV6JCds8vAdZyrhDBz0woEXQ" +
                        "5aBjm8Ur6JpAu3q3frfS1CF1b_-CfIKB1zbP7JWpyCq-g-cUwYIc2vvdsYUdV_KO4" +
                        "u5SYFZI3Jqk4PJLim3WBw_50v7apYx9BUNyHneg-D_KtgDleZMXOJNdyyuM0SO2kL" +
                        "Q_5X_2Vrjn6ka6YPCVIzauhVrRfDezAkJwA",
                "abcd1234",
                3600L,
                false
        )
    }

    override fun initAccount(): Completable {
        return Completable.fromAction {
            inMemoryAccount = if (inMemoryAccount == null) {
                accountManager.getAccountsByType(accountType).firstOrNull().let {
                    if (it == null) null
                    else {
                        // listen to any changes of accounts
                        if (!isInitialized.getAndSet(true)) {
                            accountManager.addOnAccountsUpdatedListener(this@AccountLocalDataSource, null, true)
                        }

                        accountManager.let { manager ->
                            inMemoryAccount = AccountInfo(
                                    manager.getUserData(it, ACCOUNT_MANAGER_USER_ID),
                                    manager.getUserData(it, ACCOUNT_MANAGER_REFRESH_TOKEN),
                                    manager.getUserData(it, ACCOUNT_MANAGER_ID_TOKEN),
                                    manager.getUserData(it, ACCOUNT_MANAGER_ID_TOKEN_EXPIRED_IN).toLong(),
                                    manager.getUserData(it, ACCOUNT_MANAGER_IS_ANONYMOUS)?.toBoolean() ?: false)
                            inMemoryAccount
                        }
//                        debugAccount
                    }
                }
            }
            else inMemoryAccount
        }
    }

    override fun getAccount() = inMemoryAccount

    override fun refreshToken(refreshToken: String?): Flowable<AccountInfo> {
        return getAccount().let {
            if (it == null) throw NoRefreshTokenException()
            else Flowable.just(it)
        }
    }

    override fun saveAccount(account: AccountInfo): Flowable<AccountInfo> {
        return Flowable.fromCallable {
            // save account info to in-memory cache and one in AccountManager
            createOrUpdateAccountInAccountManager(account)

            inMemoryAccount = account
            inMemoryAccount
        }
    }

    override fun signInWithEmailPassword(email: CharArray, password: CharArray): Flowable<AccountInfo> {
        return Flowable.empty()
    }

    override fun signUpAnonymously(): Flowable<AccountInfo> {
        return Flowable.empty()
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    private fun createOrUpdateAccountInAccountManager(account: AccountInfo) {
        accountManager.getAccountsByType(accountType).firstOrNull().let {
            if (it == null) accountManager.addAccountExplicitly(Account(account.userId, accountType), null, Bundle().apply {
                putString(ACCOUNT_MANAGER_USER_ID, account.userId)
                putString(ACCOUNT_MANAGER_REFRESH_TOKEN, account.refreshToken)
                putString(ACCOUNT_MANAGER_ID_TOKEN, account.idToken)
                putString(ACCOUNT_MANAGER_ID_TOKEN_EXPIRED_IN, account.expiresIn.toString())
                account.isAnonymous?.let { value ->
                    putString(ACCOUNT_MANAGER_IS_ANONYMOUS, value.toString())
                }
            })
            else {
                accountManager.apply {
                    setUserData(it, ACCOUNT_MANAGER_USER_ID, account.userId)
                    setUserData(it, ACCOUNT_MANAGER_REFRESH_TOKEN, account.refreshToken)
                    setUserData(it, ACCOUNT_MANAGER_ID_TOKEN, account.idToken)
                    setUserData(it, ACCOUNT_MANAGER_ID_TOKEN_EXPIRED_IN, account.expiresIn.toString())
                    account.isAnonymous?.let { value ->
                        setUserData(it, ACCOUNT_MANAGER_IS_ANONYMOUS, value.toString())
                    }
                }
            }
        }
    }

    override fun signOut(): Completable {
        return Completable.fromAction {
            val account = accountManager.getAccountsByType(accountType).firstOrNull()
            account ?: return@fromAction

            accountManager.removeAccountExplicitly(account)
            inMemoryAccount = null
        }
    }

    override fun onAccountsUpdated(accounts: Array<Account>?) {
        if (accounts.isNullOrEmpty()) {
            inMemoryAccount = null
        }
    }
}
