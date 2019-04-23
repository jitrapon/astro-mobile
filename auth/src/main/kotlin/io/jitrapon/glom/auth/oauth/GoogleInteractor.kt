package io.jitrapon.glom.auth.oauth

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.os.Parcelable
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import io.jitrapon.glom.auth.AuthView
import io.jitrapon.glom.auth.R
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult

class GoogleInteractor : BaseOauthInteractor {

    private var client: GoogleSignInClient? = null

    private var completeHandler: ((AsyncResult<String>) -> Unit)? = null

    private fun getClient(activity: Activity): GoogleSignInClient {
        if (client == null) {
            client = GoogleSignIn.getClient(activity,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(activity.getString(io.jitrapon.glom.R.string.google_oauth_server_key))
                    .requestEmail()
                    .build())
        }
        return client!!
    }

    override fun acquireToken(authView: AuthView?, onComplete: (AsyncResult<String>) -> Unit) {
        if (authView == null || authView !is Activity) {
            onComplete(AsyncErrorResult(Exception("An instance object that implements AuthView interface is not provided")))
            return
        }

        // check if user has already signed in before with Google, if so, retrieve the idToken
        val idToken = GoogleSignIn.getLastSignedInAccount(authView)?.idToken
        if (idToken != null) {
            onComplete(AsyncSuccessResult(idToken))
        }

        // user has not previosuly signed in
        else {
            completeHandler = onComplete

            getClient(authView).silentSignIn().addOnCompleteListener {
                try {
                    when {
                        it.isSuccessful -> {
                            it.result.idToken.let { token ->
                                if (token == null) {
                                    onComplete(AsyncErrorResult(BaseOauthInteractor.NoAccessTokenException()))
                                }
                                else {
                                    onComplete(AsyncSuccessResult(token))
                                }
                            }
                        }
                        it.isCanceled -> onComplete(AsyncErrorResult(BaseOauthInteractor.OperationCanceledException()))
                        else -> {
                            val ex = it.exception
                            if (ex is ApiException) {
                                if (ex.statusCode == GoogleSignInStatusCodes.SIGN_IN_REQUIRED) {
                                    authView.onRequireSignIn(getClient(authView).signInIntent)
                                }
                                else onComplete(AsyncErrorResult(BaseOauthInteractor.OperationFailedException(it.exception)))
                            }
                            else onComplete(AsyncErrorResult(BaseOauthInteractor.OperationFailedException(it.exception)))
                        }
                    }
                }
                catch (ex: Exception) {
                    onComplete(AsyncErrorResult(BaseOauthInteractor.OperationFailedException(ex)))
                }
            }
        }
    }

    override fun processOauthResult(authView: AuthView?, requestCode: Int, resultCode: Int, data: Parcelable?) {
        if (resultCode == RESULT_OK) {
            completeHandler?.let {
                acquireToken(authView, it)
            }
        }
    }
}
