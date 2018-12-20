package io.jitrapon.glom.auth.oauth

import android.os.Parcelable
import io.jitrapon.glom.auth.AuthView
import io.jitrapon.glom.base.model.AsyncResult

/**
 * Base interface for implementing third-party social oauth login for acquiring
 * access tokens. The interactor may require an instance of an Android presentation layer, which
 * will be passed via AuthView
 *
 * @author Jitrapon Tiachunpun
 */
interface BaseOauthInteractor {

    /**
     * Asynchronously retrieve an access token provided by the third-party oauth provider
     */
    fun acquireToken(authView: AuthView?, onComplete: (AsyncResult<String>) -> Unit)

    /**
     * Process the result from Oauth library
     */
    fun processOauthResult(requestCode: Int, resultCode: Int, data: Parcelable?)

    /**
     * This exception should be used to indicate that the user has manually cancelled the authentication exception
     */
    class OperationCanceledException : Exception("User has cancelled the authentication process")

    /**
     * This exception should be used to indicate that the authentication operation may have succeeded
     * but that there is no access token available
     */
    class NoAccessTokenException : Exception("Access token is NULL or invalid")

    /**
     * This exception should bev used to indicate that the login operation has failed with a cause
     */
    class OperationFailedException(cause: Exception) : Exception("Operation failed", cause)
}
