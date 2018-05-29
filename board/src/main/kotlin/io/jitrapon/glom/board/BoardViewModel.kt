package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.support.v4.util.ArrayMap
import android.support.v7.util.DiffUtil
import android.view.View
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.base.viewmodel.runAsync
import io.jitrapon.glom.board.event.EventItemUiModel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.collections.HashMap
import kotlin.math.absoluteValue

/**
 * ViewModel class responsible for showing and interacting with the Board
 *
 * Created by Jitrapon on 11/18/2017.
 */
class BoardViewModel : BaseViewModel() {

    @Inject
    lateinit var boardInteractor: BoardInteractor

    @Inject
    lateinit var circleInteractor: CircleInteractor

    /* live data for the board items */
    internal val observableBoard = MutableLiveData<BoardUiModel>()
    internal val boardUiModel = BoardUiModel()

    /* live event for full-screen animation */
    internal val observableAnimation = LiveEvent<AnimationItem>()

    /* live data for selected board item and list of shared views for transition */
    private val observableBoardItem = LiveEvent<Triple<BoardItem, List<Pair<View, String>>?, Boolean>>()

    /* whether or not first load function has been called */
    private var firstLoadCalled: Boolean = false

    /* default filtering type of items */
    private var itemFilterType: ItemFilterType = ItemFilterType.EVENTS_BY_WEEK

    /* last grouping key that was emitted from the interactor. This is to indicate
        whether or not to append a new list of items to the last group, or to start a new group.
     */
    private var lastKeyGroup: Any? = null

    /* List that caches item indices that have not been dispatched to the observer because
       there are no active observers
     */
    private val undispatchedItemIndices: ArrayList<Pair<Int, Any?>> by lazy {
        ArrayList<Pair<Int, Any?>>()
    }

    /*
     * ID counter for error ui model
     */
    private val errorIdCounter = AtomicInteger(0)

    /*
     * Keeps track of currently syncing items and their statuses
     */
    private val syncItemIds = HashMap<String, UiModel.Status>()

    companion object {

        const val NUM_WEEK_IN_YEAR = 52
        const val FIRST_LOAD_ANIM_DELAY = 0L
        const val SUBSEQUENT_LOAD_ANIM_DELAY = 300L
    }

    init {
        BoardInjector.getComponent().inject(this)

        boardInteractor.setFilteringType(itemFilterType)

        loadActiveCircleInfo()
        loadBoard(false)
    }

    //region board actions

    /**
     * Loads board data and items asynchronously
     *
     * @param refresh If true, old data will be discarded and will be refreshed from the server again
     */
    fun loadBoard(refresh: Boolean) {
        observableBoard.value = boardUiModel.apply {
            status = UiModel.Status.LOADING
            saveItem = null
        }

        loadData(refresh, boardInteractor::loadBoard, if (!firstLoadCalled) FIRST_LOAD_ANIM_DELAY else SUBSEQUENT_LOAD_ANIM_DELAY) {
            when (it) {
                is AsyncSuccessResult -> onBoardItemChanges(it.result.second, listOf())
                is AsyncErrorResult -> {
                    handleError(it.error)

                    observableBoard.value = boardUiModel.apply {
                        saveItem = null
                        itemsChangedIndices = null

                        // if items have been loaded successfully before, show them as it is
                        status = if (items.isNullOrEmpty()) UiModel.Status.ERROR else UiModel.Status.SUCCESS
                    }
                }
            }
        }
        firstLoadCalled = true
    }

    /**
     * Handle changes to board item list, applying DiffUtil if necessary
     */
    private fun onBoardItemChanges(data: ArrayMap<*, List<BoardItem>>, requiredPlaceitemIds: List<String>?, newItem: BoardItem? = null) {
        errorIdCounter.set(0)

        runAsync({
            data.toUiModel().let {
                it to if (boardUiModel.items.isNullOrEmpty()) null else
                    DiffUtil.calculateDiff(BoardItemDiffCallback(boardUiModel.items, it), true)
            }
        }, onComplete = { (uiModel, diff) ->
            observableBoard.value = boardUiModel.apply {
                itemsChangedIndices = null
                status = if (uiModel.isEmpty()) UiModel.Status.EMPTY else UiModel.Status.SUCCESS
                items = uiModel.toMutableList()
                diffResult = diff
                requestPlaceInfoItemIds = requiredPlaceitemIds
                saveItem = newItem
            }
        }, onError = {
            handleError(it)

            observableBoard.value = boardUiModel.apply {
                saveItem = null
                itemsChangedIndices = null

                // if items have been loaded successfully before, show them as it is
                status = if (items.isNullOrEmpty()) UiModel.Status.ERROR else UiModel.Status.SUCCESS
            }
        })
    }

    /**
     * Retrieves place information for board items that need to show them.
     *
     * @param placeProvider Subclass of PlaceProvider that implements get place info
     */
    fun loadPlaceInfo(placeProvider: PlaceProvider?, itemIds: List<String>?) {
        boardInteractor.loadItemPlaceInfo(placeProvider, itemIds) {
            when (it) {
                is AsyncSuccessResult -> {
                    if (!it.result.isEmpty) {
                        observableBoard.value = boardUiModel.apply {
                            saveItem = null
                            requestPlaceInfoItemIds = null
                            itemsChangedIndices = ArrayList()
                            diffResult = null
                            val map = it.result
                            items?.let {
                                it.forEachIndexed { index, item ->
                                    if (map.containsKey(item.itemId)) {
                                        val changePayload = item.updateLocationText(map[item.itemId])
                                        itemsChangedIndices?.add(index to arrayListOf(changePayload))
                                    }
                                }
                            }
                        }
                    }
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                }
            }
        }
    }

    /**
     * Displays new board item view
     */
    fun showEmptyNewItem(itemType: Int) {
        observableBoardItem.value = Triple(boardInteractor.createEmptyItem(itemType), null, true)
    }

    /**
     * Expands current board item (specified by position in the recyclerview)
     *
     * @param position The position of the item to view in the RecyclerView
     * @param transitionViews The shared views to allow smooth transition animation between activities
     */
    fun viewItemDetail(position: Int, transitionViews: List<Pair<View, String>>? = null) {
        boardUiModel.items?.getOrNull(position)?.let {
            boardInteractor.getBoardItem(it.itemId)?.let {
                observableBoardItem.value = Triple(it, transitionViews, false)
            }
        }
    }

    fun addNewItem(item: BoardItem) {
        boardInteractor.addItem(item) {
            when (it) {
                is AsyncSuccessResult -> onBoardItemChanges(it.result, listOf(), item)
                is AsyncErrorResult -> handleError(it.error)
            }
        }
    }

    /**
     * Persists the changes in board item with a new info
     */
    fun syncItem(boardItem: BoardItem, createNew: Boolean) {
        boardUiModel.items?.let { items ->
            val index = items.indexOfFirst { boardItem.itemId == it.itemId }

            // if item is found
            if (index != -1) {

                // show the user visually that this item is syncing
                items[index] = boardItem.toUiModel(UiModel.Status.LOADING)
                syncItemIds[items[index].itemId] = UiModel.Status.LOADING

                observableBoard.value = boardUiModel.apply {
                    items[index].itemId.let {
                        requestPlaceInfoItemIds = if (boardInteractor.hasPlaceInfo(boardItem)) listOf(it) else null
                    }
                    diffResult = null
                    itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply {
                        add(index to null)

                        // for pre-Nougat, LiveData are not triggered to observers when the
                        // observers go inactive. Therefore, we need to undispatch any pending LiveData here
                        undispatchedItemIndices.let {
                            if (!it.isNullOrEmpty()) {
                                addAll(it)
                                it.clear()
                            }
                        }
                    }
                    saveItem = null
                }

                // start syncing data to server and local database
                if (createNew) {
                    syncItem(boardInteractor::createItem, boardItem, AndroidString(R.string.board_item_created))
                }
                else {
                    syncItem(boardInteractor::editItem, boardItem, AndroidString(R.string.board_item_edited))
                }
            }
        }
    }

    private fun syncItem(operation: (BoardItem, ((AsyncResult<BoardItem>) -> Unit)) -> Unit, boardItem: BoardItem, successMessage: AndroidString) {
        var index: Int
        operation(boardItem, {
            boardUiModel.items?.let { items ->
                index = items.indexOfFirst { boardItem.itemId == it.itemId }
                if (index != -1) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            items[index].status = UiModel.Status.SUCCESS
                            syncItemIds.remove(items[index].itemId)

                            observableBoard.value = boardUiModel.apply {
                                saveItem = null
                                requestPlaceInfoItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply {
                                    add(index to arrayListOf(items[index].getStatusChangePayload()))
                                }
                            }
                            observableViewAction.value = Snackbar(successMessage, level = MessageLevel.SUCCESS)

                            // refresh the board item ordering
                            //TODO
//                                    loadBoard(false)
                        }
                        is AsyncErrorResult -> {
                            handleError(it.error)

                            items[index].status = UiModel.Status.ERROR
                            syncItemIds[items[index].itemId] = UiModel.Status.ERROR

                            observableBoard.value = boardUiModel.apply {
                                saveItem = null
                                requestPlaceInfoItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply {
                                    add(index to arrayListOf(items[index].getStatusChangePayload()))
                                }
                            }
                        }
                    }

                    // make sure to dispatch these indices when we set the observableBoard value again
                    if (!observableBoard.hasActiveObservers()) {
                        undispatchedItemIndices.add(index to arrayListOf(items[index].getStatusChangePayload()))
                    }
                }
            }
        })
    }

    fun deleteItem(position: Int) {
        boardUiModel.items?.let { items ->
            items.getOrNull(position)?.let { item ->
                if (item.itemType != BoardItemUiModel.TYPE_HEADER) {
                    boardInteractor.deleteItemLocal(item.itemId) {
                        when (it) {
                            is AsyncSuccessResult -> {
                                syncItemIds.remove(item.itemId)
                                onBoardItemChanges(it.result, listOf(), null)

                                syncDeletedItem(item.itemId)
                            }
                            is AsyncErrorResult -> handleError(it.error)
                        }
                    }
                }
            }
        }
    }

    private fun syncDeletedItem(itemId: String) {
        boardInteractor.deleteItemRemote(itemId) {
            when (it) {
                is AsyncSuccessResult -> observableViewAction.value = Snackbar(AndroidString(R.string.board_item_deleted), level = MessageLevel.SUCCESS)
                is AsyncErrorResult -> handleError(it.error)
            }
        }
    }

    //endregion
    //region circle actions

    /**
     * Loads information about this circle, specifically name, avatar, and places
     */
    private fun loadActiveCircleInfo() {
        circleInteractor.loadCircle(true, {
            when (it) {
                is AsyncSuccessResult -> {
                    //TODO display circle info in board
                }
                is AsyncErrorResult -> {
                    handleError(it.error)
                }
            }
        })
    }

    //endregion
    //region utility functions

    /**
     * Converts the BoardItem domain model to a list of BoardItemUIModel
     */
    private fun ArrayMap<*, List<BoardItem>>.toUiModel(): List<BoardItemUiModel> {
        if (isEmpty || (keys.size == 1 && this[keyAt(0)]!!.isEmpty())) return ArrayList()

        val map = this
        return ArrayList<BoardItemUiModel>().apply {
            for ((keyIndex, key) in keys.withIndex()) {
                if (keyIndex == map.size - 1) lastKeyGroup = key
                add(HeaderItemUiModel(AndroidString(
                        resId = when {
                            (key is Int && key < -1) -> {
                                R.string.event_card_header_last_n_weeks
                            }
                            (key is Int && key == -1) -> {
                                R.string.event_card_header_last_week
                            }
                            key == null -> R.string.board_item_header_no_date
                            (key is Int && key == 0) -> R.string.board_item_header_this_week
                            (key is Int && key == 1) -> R.string.board_item_header_next_week
                            (key is Int && key > 1) -> R.string.board_item_header_other_weeks
                            else -> R.string.board_item_header_undefined
                        },
                        formatArgs = if (key == null) null else {
                            if (key is Int) {
                                if (key > 1 || key < -1) arrayOf(key.absoluteValue.toString()) else null
                            } else null
                        }
                )))
                map[key]?.let { items ->
                    items.forEach { item ->
                        add(item.toUiModel(if (syncItemIds.contains(item.itemId)) syncItemIds[item.itemId]!! else UiModel.Status.SUCCESS))
                    }
                }
            }
        }
    }

    private fun BoardItem.toUiModel(status: UiModel.Status = UiModel.Status.SUCCESS): BoardItemUiModel {
        return BoardItemViewModelStore.obtainViewModelForItem(this::class.java).let {
            it?.toUiModel(this, status) ?: ErrorItemUiModel(errorIdCounter.getAndIncrement().toString())
        }
    }

    /**
     * Clean up any resources
     */
    override fun onCleared() {
        BoardItemViewModelStore.clear()
        BoardInjector.clear()
    }

    //endregion
    //region observables

    /**
     * Returns an observable board item live data for the view
     */
    fun getObservableBoard(): LiveData<BoardUiModel> = observableBoard

    /**
     * Returns an observable animation item
     */
    fun getObservableAnimation(): LiveData<AnimationItem> = observableAnimation

    /**
     * Returns an observable that if set, becomes the currently selected item
     */
    fun getObservableBoardItem(): LiveData<Triple<BoardItem, List<Pair<View, String>>?, Boolean>> = observableBoardItem

    //endregion
    //region view states

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = boardUiModel.items?.isEmpty() != false

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
            is HeaderItemUiModel -> BoardItemUiModel.TYPE_HEADER
            else -> BoardItemUiModel.TYPE_ERROR
        }
    }

    /**
     * Returns a specific Board UI item model
     */
    fun getBoardItemUiModel(position: Int): BoardItemUiModel? = boardUiModel.items.get(position, null)

    //endregion
}