package io.jitrapon.glom.board

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.AnimationItem
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.LiveEvent
import io.jitrapon.glom.base.model.MessageLevel
import io.jitrapon.glom.base.model.Navigation
import io.jitrapon.glom.base.model.Snackbar
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.util.latLng
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.base.viewmodel.runAsync
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.BoardItemDiffCallback
import io.jitrapon.glom.board.item.BoardItemUiModel
import io.jitrapon.glom.board.item.BoardItemViewModelStore
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.SyncStatus.Companion.map
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventItemUiModel
import io.jitrapon.glom.board.item.event.EventItemViewModel
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
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

    @Inject
    lateinit var userInteractor: UserInteractor

    /* live data for the board items */
    internal val observableBoard = MutableLiveData<BoardUiModel>()
    internal val boardUiModel = BoardUiModel()

    /* live event for full-screen animation */
    internal val observableAnimation = LiveEvent<AnimationItem>()

    /* live data for selected board item and list of shared views for transition */
    private val observableBoardItem = LiveEvent<Triple<BoardItem, List<Pair<android.view.View, String>>?, Boolean>>()

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

    companion object {

        const val NUM_WEEK_IN_YEAR = 52
        const val FIRST_LOAD_ANIM_DELAY = 0L
        const val SUBSEQUENT_LOAD_ANIM_DELAY = 300L
    }

    init {
        BoardInjector.getComponent().inject(this)

        boardInteractor.apply {
            itemType = BoardItem.TYPE_EVENT
            itemFilterType = ItemFilterType.EVENTS_BY_WEEK
        }

        loadBoard(false)
    }

    //region board actions

    /**
     * Loads board data and items asynchronously, default to loading event items
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
                is AsyncSuccessResult -> {
                    onBoardItemChanges(it.result.second, listOf(), listOf())

                    setUserProfileIcon(userInteractor.getCurrentUserAvatar())
                }
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
    private fun onBoardItemChanges(data: androidx.collection.ArrayMap<*, List<BoardItem>>,
                                   requiredPlaceItemIds: List<String>?, requiredAddressItemIds: List<String>?,
                                   newItem: BoardItem? = null) {
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
                requestPlaceInfoItemIds = requiredPlaceItemIds
                requestAddressItemIds = requiredAddressItemIds
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
                            requestAddressItemIds = null
                            itemsChangedIndices = ArrayList()
                            diffResult = null
                            val map = it.result
                            items?.let { items ->
                                items.forEachIndexed { index, item ->
                                    if (map.containsKey(item.itemId)) {
                                        val place = map[item.itemId]
                                        val locationTextPayload = item.updateLocationText(place?.name)
                                        val locationLatLngPayload = item.updateLocationLatLng(place?.latLng)
                                        itemsChangedIndices?.add(index to arrayListOf(locationTextPayload, locationLatLngPayload))

                                        // update this item location with the retrieved info locally
                                        // so that we don't have to fetch this info again
                                        if (item is EventItemUiModel) {
                                            (BoardItemViewModelStore.obtainViewModelForItem(
                                                EventItem::class.java
                                            ) as? EventItemViewModel)?.updateEventLocationFromPlace(item.itemId, place)
                                        }
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
     * Retrieves addresses for board items that need to show them.
     *
     * @param placeProvider Subclass of PlaceProvider that implements get place info
     */
    fun loadAddressInfo(placeProvider: PlaceProvider?, itemIds: List<String>?) {
        boardInteractor.loadItemAddressInfo(placeProvider, itemIds) {
            when (it) {
                is AsyncSuccessResult -> {
                    if (it.result.isNotEmpty()) {
                        // set the corresponding board items with the address
                        observableBoard.value = boardUiModel.apply {
                            saveItem = null
                            requestPlaceInfoItemIds = null
                            requestAddressItemIds = null
                            itemsChangedIndices = ArrayList()
                            diffResult = null
                            boardUiModel.items?.forEachIndexed { index, item ->
                                it.result[item.itemId]?.let { (name, address) ->
                                    val locationLatLngPayload = item.updateLocationLatLng(address?.latLng)
                                    itemsChangedIndices?.add(index to arrayListOf(locationLatLngPayload))

                                    if (item is EventItemUiModel) {
                                        (BoardItemViewModelStore.obtainViewModelForItem(
                                            EventItem::class.java
                                        ) as? EventItemViewModel)?.updateEventLocationFromAddress(item.itemId, name, address)
                                    }
                                }
                            }
                        }
                    }
                }
                is AsyncErrorResult -> handleError(it.error)
            }
        }
    }

    /**
     * Displays new board item view
     */
    fun showEmptyNewItem() {
        observableBoardItem.value = Triple(boardInteractor.createEmptyItem(), null, true)
    }

    /**
     * Returns the underlying board item given the position of its UIModel in the list
     */
    fun getBoardItem(position: Int): BoardItem? {
        return boardUiModel.items?.getOrNull(position)?.let {
            boardInteractor.getBoardItem(it.itemId)
        }
    }

    /**
     * Expands current board item (specified by position in the recyclerview)
     *
     * @param position The position of the item to view in the RecyclerView
     * @param transitionViews The shared views to allow smooth transition animation between activities
     */
    fun viewItemDetail(position: Int, transitionViews: List<Pair<android.view.View, String>>? = null) {
        getBoardItem(position)?.let {
            observableBoardItem.value = Triple(it, transitionViews, false)
        }
    }

    fun addNewItem(item: BoardItem) {
        boardInteractor.addItem(item) {
            when (it) {
                is AsyncSuccessResult -> onBoardItemChanges(it.result, listOf(), listOf(), item)
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
                // however, set the local copy of the item to be failed so that if the
                // async operation fails, the next time the item loads, its sync status will be interpreted as failed
                items[index] = boardItem.toUiModel(SyncStatus.ACTIVE)
                boardInteractor.setItemSyncStatus(items[index].itemId, SyncStatus.FAILED)

                observableBoard.value = boardUiModel.apply {
                    items[index].itemId.let {
                        requestPlaceInfoItemIds = if (boardInteractor.hasPlaceInfo(boardItem)) listOf(it) else null
                        requestAddressItemIds = null
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

                // start syncing data to server and local database depending on the item's sync status
                if (createNew) {
                    syncItem(boardInteractor::createItem, boardItem, AndroidString(R.string.board_item_created))
                }
                else {
                    syncItem(boardInteractor::editItem, boardItem, AndroidString(R.string.board_item_edited))
                }
            }
        }
    }

    /**
     * Syncs the item with the specified item ID again with the remote server
     */
    fun syncItem(itemId: String) {
        boardInteractor.getBoardItem(itemId)?.let { syncItem(it, false) }
    }

    private fun syncItem(operation: (BoardItem, ((AsyncResult<BoardItem>) -> Unit)) -> Unit, boardItem: BoardItem, successMessage: AndroidString) {
        var index: Int
        operation(boardItem) {
            boardUiModel.items?.let { items ->
                index = items.indexOfFirst { boardItem.itemId == it.itemId }
                if (index != -1) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            // update the sync status accordingly to the item's previous status
                            // if the item is marked as syncable, i.e. not offline, then it should be updated
                            // to success state. Otherwise, mark this item again as offline
                            items[index].status = if (boardItem.syncStatus != SyncStatus.OFFLINE)
                                UiModel.Status.SUCCESS else UiModel.Status.POSITIVE
                            boardInteractor.setItemSyncStatus(items[index].itemId,
                                if (boardItem.syncStatus != SyncStatus.OFFLINE) SyncStatus.SUCCESS
                                else SyncStatus.OFFLINE)

                            observableBoard.value = boardUiModel.apply {
                                saveItem = null
                                requestPlaceInfoItemIds = null
                                requestAddressItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply {
                                    add(index to arrayListOf(items[index].getStatusChangePayload()))
                                }
                            }
                            observableViewAction.value = Snackbar(successMessage, level = MessageLevel.SUCCESS)

                            // refresh the board item ordering
                            loadBoard(false)
                        }
                        is AsyncErrorResult -> {
                            handleError(it.error)

                            items[index].status = UiModel.Status.ERROR
                            boardInteractor.setItemSyncStatus(items[index].itemId, SyncStatus.FAILED)

                            observableBoard.value = boardUiModel.apply {
                                saveItem = null
                                requestPlaceInfoItemIds = null
                                requestAddressItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply {
                                    add(index to arrayListOf(items[index].getStatusChangePayload()))
                                }
                            }

                            // refresh the board item ordering
                            loadBoard(false)
                        }
                    }

                    // make sure to dispatch these indices when we set the observableBoard value again
                    if (!observableBoard.hasActiveObservers()) {
                        undispatchedItemIndices.add(index to arrayListOf(items[index].getStatusChangePayload()))
                    }
                }
            }
        }
    }

    fun deleteItem(position: Int) {
        boardUiModel.items?.let { items ->
            items.getOrNull(position)?.let { item ->
                if (item.itemType != BoardItemUiModel.TYPE_HEADER) {
                    boardInteractor.deleteItemLocal(item.itemId) {
                        when (it) {
                            is AsyncSuccessResult -> {
                                onBoardItemChanges(it.result, listOf(), listOf(), null)

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

    //region preferences

    fun showBoardPreference() {
        observableNavigation.value = Navigation(Const.NAVIGATE_TO_BOARD_PREFERENCE, boardInteractor.itemType)
    }

    //endregion

    //endregion
    //region utility functions

    /**
     * Converts the BoardItem domain model to a list of BoardItemUIModel
     */
    private fun androidx.collection.ArrayMap<*, List<BoardItem>>.toUiModel(): List<BoardItemUiModel> {
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
                        add(item.toUiModel(item.syncStatus))
                    }
                }
            }
        }
    }

    private fun BoardItem.toUiModel(syncStatus: SyncStatus): BoardItemUiModel {
        return BoardItemViewModelStore.obtainViewModelForItem(this::class.java).let {
            it?.toUiModel(this, syncStatus) ?: ErrorItemUiModel(errorIdCounter.getAndIncrement().toString())
        }
    }

    /**
     * Clean up any resources
     */
    override fun onCleared() {
        BoardItemViewModelStore.clear()
        BoardInjector.clear()
        boardInteractor.cleanup()
        circleInteractor.cleanup()
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
    fun getObservableBoardItem(): LiveData<Triple<BoardItem, List<Pair<android.view.View, String>>?, Boolean>> = observableBoardItem

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
