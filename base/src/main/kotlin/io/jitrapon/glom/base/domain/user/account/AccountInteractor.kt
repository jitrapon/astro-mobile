package io.jitrapon.glom.base.domain.user.account

import android.annotation.SuppressLint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Main entry point to managing user accounts
 *
 * Created by Jitrapon
 */
class AccountInteractor(private val dataSource: AccountDataSource) {

    fun getUserId(): String? = dataSource.getAccount()?.userId

    fun refreshIdToken() {

    }

    @SuppressLint("CheckResult")
    fun saveAccount(userId: String, idToken: String?, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        dataSource.saveAccount(userId, idToken)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete()
                }, {
                    onError(it)
                })
    }
}