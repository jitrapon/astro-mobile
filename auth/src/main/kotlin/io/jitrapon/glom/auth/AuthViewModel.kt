package io.jitrapon.glom.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.NAVIGATE_TO_MAIN
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.Loading
import io.jitrapon.glom.base.model.Navigation
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.Arrays
import javax.inject.Inject

/**
 * Created by Jitrapon
 */
class AuthViewModel : BaseViewModel() {

    /* observable background URL */
    private val observableBackground = MutableLiveData<String?>()

    /* observable representation of the authentication state */
    private val observableAuth = MutableLiveData<AuthUiModel>()
    private val authUiModel: AuthUiModel = AuthUiModel(false)

    @Inject
    lateinit var authInteractor: AuthInteractor

    init {
        AuthInjector.getComponent().inject(this)

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

                observableViewAction.value = Loading(true)

                authInteractor.signInWithEmailPassword(email!!, password!!) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            observableViewAction.execute(
                                arrayOf(Loading(false), Navigation(NAVIGATE_TO_MAIN, null))
                            )
                        }
                        is AsyncErrorResult -> handleError(it.error)
                    }

                    Arrays.fill(email, ' ')
                    Arrays.fill(password, ' ')
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
    }

    override fun onCleared() {
        AuthInjector.clear()
    }

    //region observable getters

    fun getObservableBackground(): LiveData<String?> = observableBackground

    fun getObservableAuth(): LiveData<AuthUiModel> = observableAuth

    //endregion
}
