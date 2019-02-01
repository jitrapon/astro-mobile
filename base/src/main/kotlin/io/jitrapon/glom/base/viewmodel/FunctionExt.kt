package io.jitrapon.glom.base.viewmodel

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Executes an array of function, by an optional amount of time in-between them, using Android's
 * Handler
 */
fun Array<() -> Unit>.run(delayBetween: Long) {
    var index = 0
    val array = this
    val handler = Handler(Looper.getMainLooper())
    val runnable = object: Runnable {
        override fun run() {
            array[index]()
            if (++index < array.size) handler.postDelayed(this, delayBetween)
        }
    }
    handler.post(runnable)
}

/**
 * Runs a given function on a thread in computation thread pool, then forward the return value
 * to onComplete on the Android's main thread.
 */
@SuppressLint("CheckResult")
fun <T> runAsync(function: () -> T, onComplete: (T) -> Unit, onError: (Throwable) -> Unit) {
    Single.fromCallable(function)
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(onComplete, onError)
}
