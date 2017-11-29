package io.jitrapon.glom.data

import io.reactivex.Flowable

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
     * The type of data source this repository retrieves its DataModel from.
     */
    abstract val type: SourceType

    /**
     * Loads a data from a data source. Must be implemented by child repository.
     */
    abstract fun load(): Flowable<T>

    /**
     * Creates or updates the specified data to a data source. Can be optionally implemented by child repository.
     */
    open fun createOrUpdate(model: T) {}
}
