package io.jitrapon.glom.auth

import io.jitrapon.glom.base.domain.user.account.AccountDataSource
import io.jitrapon.glom.base.domain.user.account.OAuthAccountInfo
import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class AuthInteractor(private val dataSource: AccountDataSource) : BaseInteractor() {

    fun signInWithEmailPassword(email: CharArray, password: CharArray, onComplete: (AsyncResult<Unit>) -> Unit) {
        dataSource.signInWithEmailPassword(email, password)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //nothing
            }).autoDispose()
    }

    fun signUpWithEmailPassword(email: CharArray, password: CharArray, onComplete: (AsyncResult<Unit>) -> Unit) {
        dataSource.signUpWithEmailPassword(email, password, accountDataSource.getAccount()?.idToken)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //nothing
            }).autoDispose()
    }

    fun signInWithOAuthCredential(oAuthToken: String, onComplete: (AsyncResult<OAuthAccountInfo>) -> Unit) {
        dataSource.signInWithOAuthCredential(oAuthToken)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //nothing
            }).autoDispose()
    }
}
