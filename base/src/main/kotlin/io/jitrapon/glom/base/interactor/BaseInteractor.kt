package io.jitrapon.glom.base.interactor

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

/**
 * Base class for all interactors. Provides all child classes with common RX instances.
 *
 * Created by Jitrapon
 */
open class BaseInteractor {

    /* shared instance of composite disposable to dispose Disposable instances not disposed automatically */
    private val compositeDisposable: CompositeDisposable by lazy { CompositeDisposable() }

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