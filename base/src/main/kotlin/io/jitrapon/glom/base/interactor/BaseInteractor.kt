package io.jitrapon.glom.base.interactor

import com.squareup.moshi.Moshi
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.account.AccountDataSource
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.ErrorResponse
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

    @Inject
    lateinit var moshi: Moshi

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
                    else Flowable.error(it.serialize())
                }
                else -> Flowable.error(it)
            }
        }
    }

    /**
     * Converts a HttpException into an Exception whose cause is the HttpException with a message
     * serialized from the error body
     */
    private fun HttpException.serialize(): Exception {
        return response()?.errorBody()?.string().let {
            if (it == null) Exception(null, this)
            else {
                var errorMessage: String? = null
                try {
                    val adapter = moshi.adapter(ErrorResponse::class.java)
                    errorMessage = adapter.fromJson(it)?.error
                }
                catch (ex: Exception) {
                    AppLogger.e(ex)
                }
                Exception(errorMessage, this)
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

    /**
     * Signs out user by invalidating remote and local user credentials
     */
    fun signOut(onComplete: (AsyncResult<Unit>) -> Unit) {
        accountDataSource.signOut()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(Unit))
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
    }
}
