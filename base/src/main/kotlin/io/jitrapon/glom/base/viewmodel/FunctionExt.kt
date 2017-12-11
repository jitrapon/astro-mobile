package io.jitrapon.glom.base.viewmodel

import android.os.Handler
import android.os.Looper

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