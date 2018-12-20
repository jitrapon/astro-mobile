package io.jitrapon.glom.auth.oauth

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import androidx.fragment.app.Fragment
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import io.jitrapon.glom.auth.AuthView
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import java.util.Arrays

class FacebookInteractor : BaseOauthInteractor {

    private val loginManager: LoginManager
        get() = LoginManager.getInstance()

    private val callbackManager: CallbackManager = CallbackManager.Factory.create()

    private val permissions = Arrays.asList("user_birthday", "public_profile", "email", "user_events", "user_location")

    override fun acquireToken(authView: AuthView?, onComplete: (AsyncResult<String>) -> Unit) {
        if (authView == null) {
            onComplete(AsyncErrorResult(Exception("An instance object that implements AuthView interface is not provided")))
            return
        }

        loginManager.apply {
            registerCallback(callbackManager, object : FacebookCallback<LoginResult> {

                override fun onSuccess(result: LoginResult?) {
                    result?.accessToken?.token.let {
                        if (it == null) onComplete(AsyncErrorResult(BaseOauthInteractor.NoAccessTokenException()))
                        else onComplete(AsyncSuccessResult(it))
                    }
                }

                override fun onCancel() {
                    onComplete(AsyncErrorResult(BaseOauthInteractor.OperationCanceledException()))
                }

                override fun onError(error: FacebookException?) {
                    onComplete(AsyncErrorResult(
                        error.let {
                            if (it == null) Exception("Unknown error")
                            else BaseOauthInteractor.OperationFailedException(it)
                        }))
                }
            })

            when (authView) {
                is Activity -> logInWithReadPermissions(authView, permissions)
                is Fragment -> logInWithReadPermissions(authView, permissions)
            }
        }
    }

    override fun processOauthResult(requestCode: Int, resultCode: Int, data: Parcelable?) {
        callbackManager.onActivityResult(requestCode, resultCode, data as? Intent)
    }
}
