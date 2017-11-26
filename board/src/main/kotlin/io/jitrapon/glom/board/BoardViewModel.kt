package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.util.Log
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.jitrapon.glom.base.data.UiModel
import io.jitrapon.glom.base.util.get
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
    private val boardUiModel = BoardUiModel()

    /* interactor for the observable board */
    private lateinit var interactor: BoardInteractor

    init {
        interactor = BoardInteractor()
        loadBoard()
    }

    override fun isViewEmpty(): Boolean = boardUiModel.items?.isEmpty() != false

    /**
     * Loads board data
     */
    fun loadBoard() {
        observableBoard.value = boardUiModel.apply {
            status = UiModel.Status.LOADING
        }

        runBlockingIO(interactor::loadBoard) {
            when (it) {
                is AsyncSuccessResult -> {
                    it.result.items.let {
                        observableBoard.value = boardUiModel.apply {
                            status = if (it.isEmpty()) UiModel.Status.EMPTY else UiModel.Status.SUCCESS
                            items = if (it.isEmpty()) Collections.emptyList() else serializeBoardItems(it)
                        }
                    }
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                    observableBoard.value = boardUiModel.apply {
                        status = UiModel.Status.ERROR
                        items = null
                    }
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
                    EventItemUiModel(it.itemInfo.eventName, if (it.itemInfo.startTime == null) "N/A" else Date(it.itemInfo.startTime).toString(),
                            if (it.itemInfo.endTime == null) "N/A" else Date(it.itemInfo.endTime).toString())
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

    /**
     * Returns the number of board items
     */
    fun getBoardItemCount(): Int = boardUiModel.items?.size ?: 0

    /**
     * Returns the item type based on its position
     */
    fun getBoardItemType(position: Int): Int {
        return when (boardUiModel.items.get(position, null)) {
            is EventItemUiModel -> BoardItemUiModel.TYPE_EVENT
            else -> BoardItemUiModel.TYPE_ERROR
        }
    }

    /**
     * Returns a specific Board UI item model
     */
    fun getBoardItem(position: Int): BoardItemUiModel? {
        return boardUiModel.items.get(position, null)
    }
}