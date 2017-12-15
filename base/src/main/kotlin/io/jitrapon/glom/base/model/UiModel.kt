package io.jitrapon.glom.base.model

/**
 * A UiModel class encapsulates data that is ready to be presented by a View through ViewModel. Classes
 * that implements this interface are usually wrapped as LiveData.
 *
 * @author Jitrapon Tiachunpun
 */
interface UiModel {

    enum class Status {
        SUCCESS, ERROR, EMPTY, LOADING
    }

    val status: Status
}