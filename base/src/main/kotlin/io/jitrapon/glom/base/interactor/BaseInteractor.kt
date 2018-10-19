package io.jitrapon.glom.base.interactor

import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.account.AccountDataSource
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
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
    }

    /* convenience properties for accessing the user ID */
    val userId: String?
        get() = accountDataSource.getAccount()?.userId

    /* whether or not user is signed in */
    val isSignedIn: Boolean
        get() = accountDataSource.getAccount() != null

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
}