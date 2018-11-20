package io.jitrapon.glom.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.model.LayoutChange
import io.jitrapon.glom.base.model.LiveEvent
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * Created by Jitrapon
 */
class AuthViewModel : BaseViewModel() {

    /* observable background URL */
    private val observableBackground = MutableLiveData<String?>()

    /* observable event that expands email layout */
    private val observableExpandEmailEvent = LiveEvent<LayoutChange>()

    /* whether or not the email input and password field is visible */
    private var isEmailLayoutVisible: Boolean

    init {
        observableBackground.value = "https://image.ibb.co/jxh4Xq/busy-30.jpg"
        isEmailLayoutVisible = false
    }

    fun continueWithEmail() {
        if (isEmailLayoutVisible) return

        observableExpandEmailEvent.value = LayoutChange()

        isEmailLayoutVisible = true
    }

    fun getObservableBackground(): LiveData<String?> = observableBackground

    fun getObservableExpandEmailEvent(): LiveData<LayoutChange> = observableExpandEmailEvent
}
