package io.jitrapon.glom.base.interactor

import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.account.AccountInteractor
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import javax.inject.Inject

/**
 * Base class for all interactors. Provides all child classes with common RX instances. Also shares common
 * functionality methods.
 *
 * Created by Jitrapon
 */
open class BaseInteractor {

    /* shared instance of composite disposable to dispose Disposable instances not disposed automatically */
    private val compositeDisposable: CompositeDisposable by lazy { CompositeDisposable() }

    @Inject
    lateinit var accountInteractor: AccountInteractor

    init {
        ObjectGraph.component.inject(this)
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