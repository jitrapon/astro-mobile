package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import io.jitrapon.glom.base.data.Loading
import io.jitrapon.glom.base.data.Toast
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * ViewModel class responsible for showing and interacting with the Board
 *
 * Created by Jitrapon on 11/18/2017.
 */
class BoardViewModel : BaseViewModel() {

    private val observableBoard = MutableLiveData<BoardUiModel>()

    fun getObserverableBoard(): LiveData<BoardUiModel> = observableBoard

    fun loadBoard() {
        observableViewAction.execute(arrayOf(
                Toast("Load board complete"),
                Loading(false)))
    }
}