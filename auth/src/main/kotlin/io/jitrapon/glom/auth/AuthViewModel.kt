package io.jitrapon.glom.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * Created by Jitrapon
 */
class AuthViewModel : BaseViewModel() {

//    @Inject
//    lateinit var interactor: AuthInteractor

    /* observable background URL */
    private val observableBackground = MutableLiveData<String?>()

    init {
        observableBackground.value = "https://preview.ibb.co/ktMYqA/busy.jpg"
    }

    fun getObservableBackground(): LiveData<String?> = observableBackground
}