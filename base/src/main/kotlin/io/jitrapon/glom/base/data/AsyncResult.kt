package io.jitrapon.glom.base.data

/**
 * @author Jitrapon Tiachunpun
 */
interface AsyncResult<T>

class AsyncSuccessResult<T>(val result: T) : AsyncResult<T>
class AsyncErrorResult<T>(val error: Throwable) : AsyncResult<T>