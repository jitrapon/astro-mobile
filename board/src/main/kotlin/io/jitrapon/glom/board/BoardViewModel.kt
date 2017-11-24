package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.jitrapon.glom.base.data.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.*

/**
 * ViewModel class responsible for showing and interacting with the Board
 *
 * Created by Jitrapon on 11/18/2017.
 */
class BoardViewModel : BaseViewModel() {

    /* live data for the board items */
    private val observableBoard = MutableLiveData<BoardUiModel>()

    /* interactor for the observable board */
    private lateinit var interactor: BoardInteractor

    init {
        interactor = BoardInteractor()
        loadBoard()
    }

    override fun isViewEmpty(): Boolean = observableBoard.value?.items?.isEmpty() ?: true

    /**
     * Loads board data
     */
    fun loadBoard() {
        runBlockingIO(interactor::loadBoard) {
            when (it) {
                is AsyncSuccessResult -> {
                    it.result.items.let {
                        observableBoard.value = BoardUiModel(
                                status = if (it.isEmpty()) UiModel.Status.EMPTY else UiModel.Status.SUCCESS,
                                items = if (it.isEmpty()) Collections.emptyList() else serializeBoardItems(it)
                        )
                    }
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                    BoardUiModel(UiModel.Status.ERROR)
                }
            }
        }
    }

    /**
     * Converts the BoardItem domain model to a list of BoardItemUIModel
     */
    private fun serializeBoardItems(items: List<BoardItem>): List<BoardItemUiModel> {
        return items.map {
            when (it) {
                is EventItem -> {
                    EventItemUiModel(it.itemInfo.eventName, it.itemInfo.startTime, it.itemInfo.endTime)
                }
                else -> {
                    ErrorItemUiModel()
                }
            }
        }
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
    fun getObservableBoard(): LiveData<BoardUiModel> = observableBoard
}