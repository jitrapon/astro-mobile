package io.jitrapon.glom.base.util

import android.util.Log.ERROR
import android.util.Log.WARN
import timber.log.Timber

/**
 * @author Jitrapon Tiachunpun
 */
class ReleaseTree : Timber.Tree() {

    /**
     * Write a log message to its destination. Called for all level-specific methods by default.
     *
     * @param priority Log level. See {@link Log} for constants.
     * @param tag Explicit or inferred tag. May be {@code null}.
     * @param message Formatted log message. May be {@code null}, but then {@code t} will not be.
     * @param t Accompanying exceptions. May be {@code null}, but then {@code message} will not be.
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == ERROR || priority == WARN) {
            // call crashlytics to log non-fatal errors
            //TODO
        }
    }
}