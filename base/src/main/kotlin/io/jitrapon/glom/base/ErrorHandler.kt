package io.jitrapon.glom.base

import io.jitrapon.glom.base.util.AppLogger
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException


/**
 * @author Jitrapon Tiachunpun
 */
object ErrorHandler {

    fun init() {
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
    }
}