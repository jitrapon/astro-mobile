package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.jitrapon.glom.base.data.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.board.data.BoardItem

/**
 * ViewModel class responsible for showing and interacting with the Board
 *
 * Created by Jitrapon on 11/18/2017.
 */
class BoardViewModel : BaseViewModel() {

    /* live data for the board items */
    private val observableBoardItems = MutableLiveData<List<BoardItem>>()

    /* interactor for the observable board */
    private lateinit var interactor: BoardInteractor

    init {
        interactor = BoardInteractor()
        loadBoard()
    }

    /**
     * Loads board data
     */
    fun loadBoard() {
        loadData(interactor::loadBoardItems, observableBoardItems.value?.isEmpty(), {
            when (it) {
                is AsyncSuccessResult -> {
                    BoardUiModel(items = it.result)
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                    BoardUiModel(UiModel.Status.ERROR)
                }
            }
        })
    }

    /**
     * Clean up any resources
     */
    override fun onCleared() {
        //nothing yet
        Log.d("BoardViewModel", "onCleared()")
    }

    /**
     * Returns an observable board item live data for the view
     */
    fun getObservableBoardItems(): LiveData<List<BoardItem>> = observableBoardItems
}