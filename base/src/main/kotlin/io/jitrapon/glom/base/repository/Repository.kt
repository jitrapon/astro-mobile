package io.jitrapon.glom.base.repository

import io.jitrapon.glom.base.model.DataModel

/**
 * Base class for all repositories. A repository maps one-to-one to a model class implementing
 * the DataModel interface, and nothing more. A repository whole function is to perform CRUD operations
 * on a DataModel. All repositories must implement the base Load function, and optionally other
 * operations.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class Repository<T> where T : DataModel
