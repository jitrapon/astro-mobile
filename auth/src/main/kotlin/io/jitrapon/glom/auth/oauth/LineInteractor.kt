package io.jitrapon.glom.auth.oauth

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Parcelable
import com.linecorp.linesdk.LineApiResponseCode
import com.linecorp.linesdk.Scope
import com.linecorp.linesdk.auth.LineAuthenticationParams
import com.linecorp.linesdk.auth.LineLoginApi
import io.jitrapon.glom.auth.AuthView
import io.jitrapon.glom.auth.R
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import java.util.Arrays

class LineInteractor : BaseOauthInteractor {

    private val RESULT_CODE_LINE_LOGIN = 5000

    private var completeHandler: ((AsyncResult<String>) -> Unit)? = null

    private val permissions = Arrays.asList(Scope.PROFILE)

    override fun acquireToken(authView: AuthView?, onComplete: (AsyncResult<String>) -> Unit) {
        if (authView == null || authView !is Activity) {
            onComplete(AsyncErrorResult(Exception("An instance object that implements AuthView interface is not provided")))
            return
        }

        try {
            completeHandler = onComplete

            val intent = LineLoginApi.getLoginIntent(
                authView,
                authView.getString(R.string.line_channel_id),
                LineAuthenticationParams.Builder().scopes(permissions).build()
            )
            authView.startActivityForResult(intent, RESULT_CODE_LINE_LOGIN)
        }
        catch (ex: Exception) {
            onComplete(AsyncErrorResult(Exception(BaseOauthInteractor.OperationFailedException(ex))))
        }
    }

    override fun processOauthResult(authView: AuthView?, requestCode: Int, resultCode: Int, data: Parcelable?) {
        if (requestCode == RESULT_CODE_LINE_LOGIN) {
            // check if login result is canceled
            if (resultCode != RESULT_OK || data == null) {
                completeHandler?.invoke(AsyncErrorResult(BaseOauthInteractor.OperationCanceledException()))
            }
            else {
                val result = LineLoginApi.getLoginResultFromIntent(data as Intent)
                when {
                    result.responseCode == LineApiResponseCode.SUCCESS -> result.lineCredential?.accessToken?.tokenString.let {
                        if (it == null) completeHandler?.invoke(AsyncErrorResult(BaseOauthInteractor.NoAccessTokenException()))
                        else completeHandler?.invoke(AsyncSuccessResult(it))
                    }
                    result.responseCode == LineApiResponseCode.CANCEL -> {
                        completeHandler?.invoke(AsyncErrorResult(BaseOauthInteractor.OperationCanceledException()))
                    }
                    else -> completeHandler?.invoke(AsyncErrorResult(BaseOauthInteractor.OperationFailedException(Exception(result.errorData.toString()))))
                }
            }
        }
    }
}
