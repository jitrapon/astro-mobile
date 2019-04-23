package io.jitrapon.glom.base

import android.os.Looper
import io.jitrapon.glom.base.util.AppLogger
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException


/**
 * @author Jitrapon Tiachunpun
 */
object RxPlugins {

    fun init() {
        // set error handler
        RxJavaPlugins.setErrorHandler { ex ->
            if (ex is UndeliverableException) {
                ex.cause?.let {
                    AppLogger.e(it)
                }
                return@setErrorHandler
            }
            if (ex is IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (ex is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (ex is NullPointerException || ex is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), ex)
                return@setErrorHandler
            }
            if (ex is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), ex)
                return@setErrorHandler
            }
        }

        // set AndroidSchedulers
        // https://medium.com/@sweers/rxandroids-new-async-api-4ab5b3ad3e93
        RxAndroidPlugins.setInitMainThreadSchedulerHandler {
            AndroidSchedulers.from(Looper.getMainLooper(), true)
        }
    }
}