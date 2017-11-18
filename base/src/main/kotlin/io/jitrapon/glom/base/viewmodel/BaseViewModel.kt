package io.jitrapon.glom.base.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import io.jitrapon.glom.base.data.LiveEvent
import io.jitrapon.glom.base.data.UiActionModel

/**
 * Base class for all ViewModel classes
 *
 * Created by Jitrapon
 */
abstract class BaseViewModel : ViewModel() {

    /* subclass of this class should set appropriate UiActionModel to this variable to emit action to the view */
    protected val observableViewAction = LiveEvent<UiActionModel>()

    fun getObservableViewAction(): LiveData<UiActionModel> = observableViewAction
}