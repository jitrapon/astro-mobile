package io.jitrapon.glom.base.interactor

import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.account.AccountDataSource
import io.jitrapon.glom.base.domain.user.account.AccountLocalDataSource
import io.jitrapon.glom.base.util.AppLogger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Named

/**
 * Base class for all interactors. Provides all child classes with common RX instances. Also shares common
 * functionality methods.
 *
 * Created by Jitrapon
 */
open class BaseInteractor {

    /* shared instance of composite disposable to dispose Disposable instances not disposed automatically */
    private val compositeDisposable: CompositeDisposable by lazy { CompositeDisposable() }

    @field:[Inject Named("accountRepository")]
    lateinit var accountDataSource: AccountDataSource

    init {
        ObjectGraph.component.inject(this)

        //TODO should only be called once for splash screen activity's interactor
        accountDataSource.initAccount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    AppLogger.i("Initialized account complete")
                }, {
                    AppLogger.e(it)
                }).autoDispose()
    }

    /* convenience properties for accessing the user ID */
    val userId: String?
        get() = accountDataSource.getAccount()?.userId

    /* whether or not user is signed in */
    val isSignedIn: Boolean
        get() = accountDataSource.getAccount()?.userId != null

    /**
     * Check if the flowable's throwable is of type UnauthorizedException, if so perform
     * a token refresh, otherwise propogate the error to a new Flowable instance.
     */
    fun errorIsUnauthorized(error: Flowable<Throwable>): Flowable<Any> {
        return error.flatMap {
            when (it) {
                is HttpException -> {
                    if (it.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        Flowable.just(accountDataSource.refreshToken().blockingFirst())
                    }
                    else Flowable.error(it)
                }
                else -> Flowable.error(it)
            }
        }
    }

    /**
     * Disposes all disposable instances
     */
    fun cleanup() {
        compositeDisposable.clear()
    }

    /**
     * Adds this disposable to the CompositeDisposable instances
     */
    fun Disposable.autoDispose() {
        compositeDisposable.add(this)
    }

    fun testSaveDebugAccount(onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        accountDataSource.saveAccount(AccountLocalDataSource.debugAccount)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete()
                }, {
                    onError(it)
                }, {
                    //not handled
                }).autoDispose()
    }
}