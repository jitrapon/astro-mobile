package io.jitrapon.glom.base.repository

import io.jitrapon.glom.base.model.DataModel
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

/**
 * Base class for all repositories. A repository maps one-to-one to a model class implementing
 * the DataModel interface, and nothing more. A repository whole function is to perform CRUD operations
 * on a DataModel. All repositories must implement the base Load function, and optionally other
 * operations.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class Repository<T> where T : DataModel {

    fun create(localDataSourceCompletable: Completable, remoteDataSourceCompletable: Completable, parallel: Boolean = true): Completable {
        return if (parallel) {
            Completable.mergeArray(localDataSourceCompletable, remoteDataSourceCompletable)
        }
        else {
            remoteDataSourceCompletable.andThen(localDataSourceCompletable)
        }
    }

    fun load(refresh: Boolean, localDataSourceFlowable: Flowable<T>,
                                remoteDataSourceFlowable: Flowable<T>,
                                saveToLocalFlowable: (remoteData: T) -> Flowable<T>): Flowable<T> {
        return if (refresh) {
            remoteDataSourceFlowable.flatMap {
                saveToLocalFlowable(it)
            }
        }
        else {
            localDataSourceFlowable
        }
    }

    fun loadList(refresh: Boolean, localDataSourceFlowable: Flowable<List<T>>,
                 remoteDataSourceFlowable: Flowable<List<T>>,
                 saveToLocalFlowable: (remoteData: List<T>) -> List<T>): Flowable<List<T>> {
        return if (refresh) {
            remoteDataSourceFlowable.flatMap {
                Flowable.fromCallable {
                    it.forEach {
                        it.retrievedTime = Date()
                    }
                    saveToLocalFlowable(it)
                }
            }
        }
        else {
            localDataSourceFlowable
        }
    }

    fun update(localDataSourceCompletable: Completable, remoteDataSourceCompletable: Completable, parallel: Boolean = true): Completable {
        return if (parallel) {
            Completable.mergeArray(localDataSourceCompletable, remoteDataSourceCompletable)
        }
        else {
            remoteDataSourceCompletable.andThen(localDataSourceCompletable)
        }
    }

    fun delete(localDataSourceCompletable: Completable, remoteDataSourceCompletable: Completable, parallel: Boolean = true): Completable {
        return if (parallel) {
            Completable.mergeArray(localDataSourceCompletable, remoteDataSourceCompletable)
        }
        else {
            remoteDataSourceCompletable.andThen(localDataSourceCompletable)
        }
    }
}
