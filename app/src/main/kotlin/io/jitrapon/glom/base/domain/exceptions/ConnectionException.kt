package io.jitrapon.glom.base.domain.exceptions

/**
 * Unified exception raised when attempting to connect to a remote server fails
 */
class ConnectionException(cause: Throwable) : Exception(null, cause)
