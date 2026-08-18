package io.jitrapon.astro.data

/**
 * The outcome of an operation that can fail: either the value it produced or the exception that
 * stopped it.
 *
 * This is the project's error-handling type — every data-layer call that can fail returns one, and
 * [io.jitrapon.astro.data.calendar.CalendarScreenRepository] is its first production caller.
 * Failures a caller can act on arrive as [Error] rather than as a thrown exception, so a call site
 * cannot forget to handle one; cancellation is deliberately not modelled here and propagates
 * instead.
 */
sealed class Result<out T : Any> {

    data class Success<out T : Any>(val data: T) : Result<T>()

    data class Error(val exception: Exception) : Result<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$exception]"
        }
    }
}
