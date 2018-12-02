package io.jitrapon.glom.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.NAVIGATE_TO_MAIN
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.base.viewmodel.run
import retrofit2.HttpException
import java.util.*
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

    /* observable sign-in hints in case of email address */
    private val observableCredentialPicker = LiveEvent<CredentialPickerUiModel>()

    /* observable smart lock auto-login request */
    private val observableCredentialRequest = LiveEvent<CredentialRequestUiModel>()

    /* observable smart lock save request */
    private val observableCredentialSave = LiveEvent<CredentialSaveUiModel>()

    @Inject
    lateinit var authInteractor: AuthInteractor

    init {
        AuthInjector.getComponent().inject(this)

        observableBackground.value = "https://image.ibb.co/jxh4Xq/busy-30.jpg"

        requestCredential()
    }

    fun continueWithEmail(email: CharArray? = null, password: CharArray? = null) {
        if (!authUiModel.showEmailExpandableLayout) {
            // expand menu and show credential hints
            arrayOf({
                observableAuth.value = authUiModel.apply { showEmailExpandableLayout = true }
            }, {
                observableCredentialPicker.value = CredentialPickerUiModel(true, true)
            }).run(500L)
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

                signInWithPassword(email!!, password!!)
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

    fun signInWithPassword(email: CharArray, password: CharArray) {
        observableViewAction.value = Loading(true)

        authInteractor.signInWithEmailPassword(email, password) {
            when (it) {
                is AsyncSuccessResult -> {
                    observableViewAction.value = Loading(false)

                    observableCredentialSave.value = CredentialSaveUiModel(email, password, AccountType.PASSWORD)

                    Arrays.fill(email, ' ')
                    Arrays.fill(password, ' ')
                }
                is AsyncErrorResult -> {
                    handleError(it.error)

                    // if the cause of the error is a HttpException, delete the stored credentials
                    if (it.error.cause is HttpException) {
                        observableCredentialSave.value = CredentialSaveUiModel(email, password, AccountType.PASSWORD, shouldDelete = true)
                    }

                    Arrays.fill(email, ' ')
                    Arrays.fill(password, ' ')
                }
            }
        }
    }

    fun onSaveCredentialCompleted() {
        observableViewAction.value = Navigation(NAVIGATE_TO_MAIN)
    }

    private fun requestCredential() {
        observableCredentialRequest.value = CredentialRequestUiModel(true)
    }

    override fun onCleared() {
        AuthInjector.clear()
    }

    //region observable getters

    fun getObservableBackground(): LiveData<String?> = observableBackground

    fun getObservableAuth(): LiveData<AuthUiModel> = observableAuth

    fun getCredentialPickerUiModel(): LiveData<CredentialPickerUiModel> = observableCredentialPicker

    fun getCredentialRequestUiModel(): LiveData<CredentialRequestUiModel> = observableCredentialRequest

    fun getCredentialSaveUiModel(): LiveData<CredentialSaveUiModel> = observableCredentialSave

    //endregion
}
