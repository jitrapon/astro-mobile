package io.jitrapon.glom.base.util

import android.content.Context
import android.text.TextUtils
import io.jitrapon.glom.BuildConfig
import timber.log.Timber

/**
 * Central utility class to handle all logging
 *
 * @author Jitrapon Tiachunpun
 */
object AppLogger {

    /**
     * Must be called to initialize logging behaviors. Not doing so may result in no logging.
     */
    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG) Timber.plant(ReleaseTree())
        else Timber.plant(Timber.DebugTree())
    }

    /**
     * Call to log at a VERBOSE level
     */
    fun v(message: String?, vararg args: Any) {
        if (!TextUtils.isEmpty(message)) Timber.v(message, args)
    }

    /**
     * Call to log at a DEBUG level
     */
    fun d(message: String?, vararg args: Any) {
        if (!TextUtils.isEmpty(message)) Timber.d(message, args)
    }

    /**
     * Call to log at a INFO level
     */
    fun i(message: String?, vararg args: Any) {
        if (!TextUtils.isEmpty(message)) Timber.i(message, args)
    }

    /**
     * Call to log at a WARN level using a Throwable
     */
    fun w(throwable: Throwable) {
        Timber.w(throwable)
    }

    /**
     * Call to log at a WARN level with a message
     */
    fun w(message: String?, vararg args: Any) {
        if (!TextUtils.isEmpty(message)) Timber.w(message, args)
    }

    /**
     * Call to log at a ERROR level using a Throwable
     */
    fun e(throwable: Throwable) {
        Timber.e(throwable)
    }

    /**
     * Call to log at a ERROR level using a message
     */
    fun e(message: String?, vararg args: Any) {
        if (!TextUtils.isEmpty(message)) Timber.e(message, args)
    }
}