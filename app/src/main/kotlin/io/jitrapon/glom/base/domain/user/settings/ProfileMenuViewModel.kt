package io.jitrapon.glom.base.domain.user.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.R
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.*
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
        val isAnonymous = userInteractor.isSignedInAnonymously
        observableUserLoginInfo.value = LoginInfoUiModel(
                getLoginUserId(userInteractor.userId, isAnonymous),
                userInteractor.getCurrentUserAvatar(),
                getLoginButtonState(isLoading, userInteractor.isSignedIn, isAnonymous),
                getLoginInfoState(isLoading, userInteractor.isSignedIn, isAnonymous)
        )
    }

    private fun getLoginUserId(userId: String?, isAnonymous: Boolean): AndroidString {
        return when (userId) {
            null -> AndroidString(R.string.auth_not_signed_in_label)
            else -> {
                if (isAnonymous) AndroidString(R.string.auth_signed_in_anonymously) else AndroidString(text = userId)
            }
        }
    }

    private fun getLoginButtonState(isLoading: Boolean, isSignedIn: Boolean, isAnonymous: Boolean): ButtonUiModel {
        return if (isSignedIn && !isAnonymous) ButtonUiModel(AndroidString(R.string.auth_sign_out_label), null,  status = if (isLoading) UiModel.Status.LOADING else UiModel.Status.NEGATIVE)
        else ButtonUiModel(AndroidString(R.string.auth_sign_in_label), null, status = if (isLoading) UiModel.Status.LOADING else UiModel.Status.POSITIVE)
    }

    private fun getLoginInfoState(isLoading: Boolean, isSignedIn: Boolean, isAnonymous: Boolean): UiModel.Status =
            if (isLoading) UiModel.Status.LOADING else if (isSignedIn && !isAnonymous) UiModel.Status.SUCCESS else UiModel.Status.EMPTY

    fun signInOrSignOut() {
        if (userInteractor.isSignedIn && !userInteractor.isSignedInAnonymously) {
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
