package io.jitrapon.glom.base.domain.user.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.R
import io.jitrapon.glom.base.di.ObjectGraph
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.ButtonUiModel
import io.jitrapon.glom.base.model.Toast
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import javax.inject.Inject

/**
 * ViewModel class responsible for the user profile menu
 */
class ProfileMenuViewModel : BaseViewModel() {

    @Inject
    lateinit var userInteractor: UserInteractor

    private val observableUserLoginInfo = MutableLiveData<LoginInfoUiModel>()

    init {
        ObjectGraph.component.inject(this)

        loadInfo()
    }

    private fun loadInfo() {
        observableUserLoginInfo.value = LoginInfoUiModel(
                getLoginUserId(userInteractor.userId),
                userInteractor.getCurrentUserAvatar(),
                getLoginButtonState(userInteractor.isSignedIn),
                getLoginInfoState(userInteractor.isSignedIn)
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
            observableViewAction.value = Toast(AndroidString(text = "Signing out..."))
        }
        else {
            observableViewAction.value = Toast(AndroidString(text = "Signing in..."))
        }
    }

    //region getters for observables

    fun getObservableUserLoginInfo(): LiveData<LoginInfoUiModel> = observableUserLoginInfo

    //endregion
}
