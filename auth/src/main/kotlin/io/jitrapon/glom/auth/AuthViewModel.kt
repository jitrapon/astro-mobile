package io.jitrapon.glom.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.Arrays

/**
 * Created by Jitrapon
 */
class AuthViewModel : BaseViewModel() {

    /* observable background URL */
    private val observableBackground = MutableLiveData<String?>()

    /* observable representation of the authentication state */
    private val observableAuth = MutableLiveData<AuthUiModel>()
    private val authUiModel: AuthUiModel = AuthUiModel(false)

    init {
        observableBackground.value = "https://image.ibb.co/jxh4Xq/busy-30.jpg"
    }

    fun continueWithEmail(email: CharArray? = null, password: CharArray? = null) {
        if (!authUiModel.showEmailExpandableLayout) {
            observableAuth.value = authUiModel.apply { showEmailExpandableLayout = true }
        }
        else {
            val emailIsEmpty = email == null || email.isEmpty()
            val passwordIsEmpty = password == null || password.isEmpty()
            if (!emailIsEmpty && !passwordIsEmpty) {
                observableAuth.value = authUiModel.apply {
                    status = UiModel.Status.POSITIVE
                    emailError = null
                    passwordError = null
                }
            }
            else {
                observableAuth.value = authUiModel.apply {
                    status = UiModel.Status.EMPTY
                    emailError = if (emailIsEmpty) AndroidString(R.string.auth_email_empty_error) else null
                    passwordError = if (passwordIsEmpty) AndroidString(R.string.auth_password_empty_error) else null
                }
            }
        }

        email?.let {
            Arrays.fill(it, ' ')
        }
        password?.let {
            Arrays.fill(it, ' ')
        }
    }

    //region observable getters

    fun getObservableBackground(): LiveData<String?> = observableBackground

    fun getObservableAuth(): LiveData<AuthUiModel> = observableAuth

    //endregion
}
