package io.jitrapon.glom.base.model

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.os.Handler
import android.os.Looper
import android.support.annotation.MainThread
import io.jitrapon.glom.base.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A lifecycle-aware observable that sends only new updates after subscription, used for events like
 * navigation and Snackbar messages.
 *
 *
 * This avoids a common problem with events: on configuration change (like rotation) an update
 * can be emitted if the observer is active. This LiveData only calls the observable if there's an
 * explicit call to setValue() or call().
 *
 *
 * Note that only one observer is going to be notified of changes.
 *
 * @author Jitrapon Tiachunpun
 */
class LiveEvent<T : UiActionModel> : MutableLiveData<T>() {

    private val pending = AtomicBoolean(false)

    private val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<T>) {
        if (hasActiveObservers()) {
            AppLogger.w("Multiple observers registered but only one will be notified of changes.")
        }

        // Observe the internal MutableLiveData
        super.observe(owner, Observer<T> { t ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        })
    }

    @MainThread
    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        value = null
    }

    /**
     * Convenient function to run a series of LiveEvent actions, with an optional of
     * delay between actions as an argument. This can help in avoiding UI lag as multiple events
     * are fired simultaneously too close to each other. Actions are executed in the order in which
     * they were added.
     */
    fun execute(actions: Array<T?>, delayBetween: Long = 150L) {
        var index = 0
        val runnable = object: Runnable {
            override fun run() {
                value = actions[index]
                if (++index < actions.size) handler.postDelayed(this, delayBetween)
            }
        }
        handler.post(runnable)
    }
}

