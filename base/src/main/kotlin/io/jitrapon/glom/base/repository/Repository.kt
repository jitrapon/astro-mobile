package io.jitrapon.glom.base.repository

import io.jitrapon.glom.base.model.DataModel
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers

/**
 * Base class for all repositories. A repository maps one-to-one to a model class implementing
 * the DataModel interface, and nothing more. A repository whole function is to perform CRUD operations
 * on a DataModel. All repositories must implement the base Load function, and optionally other
 * operations.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class Repository<T> where T : DataModel {

    /**
     * To both tasks work in parallel subscribeOn should be called on each of the completables that would be merged.
     */
    fun create(localDataSourceCompletable: Completable, remoteDataSourceCompletable: Completable, parallel: Boolean = true): Completable {
        return if (parallel) {
            Completable.mergeArray(localDataSourceCompletable.subscribeOn(Schedulers.io()), remoteDataSourceCompletable.subscribeOn(Schedulers.io()))
        }
        else {
            remoteDataSourceCompletable.andThen(localDataSourceCompletable)
        }
    }

    inline fun load(refresh: Boolean, localDataSourceFlowable: Flowable<T>,
                                remoteDataSourceFlowable: Flowable<T>,
                                crossinline saveToLocalFlowable: (remoteData: T) -> Flowable<T>): Flowable<T> {
        return if (refresh) {
            remoteDataSourceFlowable.flatMap {
                saveToLocalFlowable(it)
            }
        }
        else {
            localDataSourceFlowable
        }
    }

    inline fun loadList(refresh: Boolean, localDataSourceFlowable: Flowable<List<T>>,
                 remoteDataSourceFlowable: Flowable<List<T>>,
                 crossinline saveToLocalFlowable: (remoteData: List<T>) -> Flowable<List<T>>): Flowable<List<T>> {
        return if (refresh) {
            remoteDataSourceFlowable.flatMap {
                saveToLocalFlowable(it)
            }
        }
        else {
            localDataSourceFlowable
        }
    }

    inline fun <E> loadTypedList(refresh: Boolean, localDataSourceFlowable: Flowable<List<E>>,
                        remoteDataSourceFlowable: Flowable<List<E>>,
                        crossinline saveToLocalFlowable: (remoteData: List<E>) -> Flowable<List<E>>): Flowable<List<E>> {
        return if (refresh) {
            remoteDataSourceFlowable.flatMap {
                saveToLocalFlowable(it)
            }
        }
        else {
            localDataSourceFlowable
        }
    }

    fun update(localDataSourceCompletable: Completable, remoteDataSourceCompletable: Completable, parallel: Boolean = true): Completable {
        return if (parallel) {
            Completable.mergeArray(localDataSourceCompletable.subscribeOn(Schedulers.io()), remoteDataSourceCompletable.subscribeOn(Schedulers.io()))
        }
        else {
            remoteDataSourceCompletable.andThen(localDataSourceCompletable)
        }
    }

    fun delete(localDataSourceCompletable: Completable, remoteDataSourceCompletable: Completable, parallel: Boolean = true): Completable {
        return if (parallel) {
            Completable.mergeArray(localDataSourceCompletable.subscribeOn(Schedulers.io()), remoteDataSourceCompletable.subscribeOn(Schedulers.io()))
        }
        else {
            remoteDataSourceCompletable.andThen(localDataSourceCompletable)
        }
    }
}
