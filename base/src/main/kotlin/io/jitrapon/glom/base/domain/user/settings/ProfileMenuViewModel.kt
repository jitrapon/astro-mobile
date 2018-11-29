package io.jitrapon.glom.base.domain.user.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.R
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.Navigation
import io.jitrapon.glom.base.model.Toast
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import javax.inject.Inject

const val NAVIGATE_TO_AUTHENTICATION = "action.navigate.authentication"

/**
 * ViewModel class responsible for the user profile menu
 */
class ProfileMenuViewModel : BaseViewModel() {

    @Inject
    lateinit var userInteractor: UserInteractor

    private val observableUserLoginInfo = MutableLiveData<LoginInfoUiModel>()

    var hasSignedOut: Boolean = false
        private set

    init {
        ObjectGraph.component.inject(this)

        updateInfo()
    }

    private fun updateInfo(isLoading: Boolean = false) {
        observableUserLoginInfo.value = LoginInfoUiModel(
                getLoginUserId(userInteractor.userId),
                userInteractor.getCurrentUserAvatar(),
                if (isLoading) ButtonUiModel(AndroidString(R.string.auth_sign_out_label), UiModel.Status.LOADING)
                        else getLoginButtonState(userInteractor.isSignedIn),
                if (isLoading) UiModel.Status.LOADING else getLoginInfoState(userInteractor.isSignedIn)
        )
    }

    private fun getLoginUserId(userId: String?): AndroidString {
        return when (userId) {
            null -> AndroidString(R.string.auth_not_signed_in_label)
            else -> AndroidString(text = userId)
        }
    }

    private fun getLoginButtonState(isSignedIn: Boolean): ButtonUiModel {
        return if (isSignedIn) ButtonUiModel(AndroidString(R.string.auth_sign_out_label), UiModel.Status.NEGATIVE)
        else ButtonUiModel(AndroidString(R.string.auth_sign_in_label), UiModel.Status.POSITIVE)
    }

    private fun getLoginInfoState(isSignedIn: Boolean): UiModel.Status =
            if (isSignedIn) UiModel.Status.SUCCESS else UiModel.Status.EMPTY

    fun signInOrSignOut() {
        if (userInteractor.isSignedIn) {
            updateInfo(true)

            userInteractor.signOut {
                updateInfo(false)

                hasSignedOut = true
                when (it) {
                    is AsyncSuccessResult -> observableViewAction.value = Toast(AndroidString(R.string.auth_sign_out_success))
                    is AsyncErrorResult -> observableViewAction.value = Toast(AndroidString(R.string.auth_sign_out_success_with_warning))
                }
            }
        }
        else {
            hasSignedOut = false

            observableViewAction.value = Navigation(NAVIGATE_TO_AUTHENTICATION, null)
        }
    }

    //region getters for observables

    fun getObservableUserLoginInfo(): LiveData<LoginInfoUiModel> = observableUserLoginInfo

    //endregion
}
