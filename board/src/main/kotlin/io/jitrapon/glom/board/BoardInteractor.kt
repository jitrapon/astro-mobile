package io.jitrapon.glom.board

import android.os.Parcel
import android.text.TextUtils
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.User
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.event.EventInfo
import io.jitrapon.glom.board.item.event.EventItem
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Interactor for dealing with Board business logic
 *
 * @author Jitrapon Tiachunpun
 */
class BoardInteractor(private val userInteractor: UserInteractor, private val boardDataSource: BoardDataSource,
                      private val circleInteractor: CircleInteractor) : BaseInteractor() {

    /*
     * The number of items that was loaded
     */
    private var itemsLoaded: Int = 0

    /*
     * Board item types
     */
    private var itemType: Int = BoardItem.TYPE_EVENT

    /*
     * Filtering type of items
     */
    private var itemFilterType: ItemFilterType = ItemFilterType.EVENTS_BY_WEEK


    //region public functions

    /**
     * Initializes the item type of items to be loaded
     */
    fun setItemType(type: Int) {
        itemType = type
    }

    /**
     * Initializes the filtering type of items when items are loaded.
     */
    fun setFilteringType(filterType: ItemFilterType) {
        itemFilterType = filterType
    }

    /**
     * Force reload of the board state, then transforms the items
     * based on the current filtering types.
     *
     * OnComplete returns the ArrayMap containing
     * keys based on the filtering type, with values containing the list of grouped items that fits
     * the criteria in each key.
     *
     * Loading of items will be executed on the IO thread pool, while processing items will be executed
     * on the computation thread pool, after which the result is observed on the Android main thread.
     */
    fun loadBoard(refresh: Boolean, onComplete: (AsyncResult<Pair<Date, androidx.collection.ArrayMap<*, List<BoardItem>>>>) -> Unit) {
        val circleId = circleInteractor.getActiveCircleId()
        Flowable.zip(boardDataSource.getBoard(circleId, itemType, refresh), userInteractor.getUsers(circleId, refresh),
                BiFunction<Board, List<User>, Pair<Board, List<User>>> { board, users ->
                    board to users
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .flatMap {
                    processItems(it.first)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    itemsLoaded = it.first.items.size
                }
                .subscribe({
                    onComplete(AsyncSuccessResult(it.first.retrievedTime!! to it.second))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                }).autoDispose()
    }

    /**
     * Adds a new board item to the list, and process it to be grouped appropriately
     */
    fun addItem(item: BoardItem, onComplete: (AsyncResult<androidx.collection.ArrayMap<*, List<BoardItem>>>) -> Unit) {
        boardDataSource.createItem(item, false)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .andThen (
                        // we need to wait until the Completable completes
                        Flowable.defer {
                            processItems(getCurrentBoard())
                        }
                )
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    itemsLoaded = it.first.items.size
                }
                .subscribe({
                    onComplete(AsyncSuccessResult(it.second))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                }).autoDispose()
    }

    /**
     * Edits this board item with a new info
     */
    fun editItem(item: BoardItem, onComplete: ((AsyncResult<BoardItem>) -> Unit)) {
        boardDataSource.editItem(item)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(item))
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
    }

    /**
     * Deletes the specified board item to the list
     */
    fun deleteItemLocal(itemId: String, onComplete: (AsyncResult<androidx.collection.ArrayMap<*, List<BoardItem>>>) -> Unit) {
        boardDataSource.deleteItem(itemId, false)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .andThen (
                    Flowable.defer {
                        processItems(getCurrentBoard())
                    }
                )
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    itemsLoaded = it.first.items.size
                }
                .subscribe({
                    onComplete(AsyncSuccessResult(it.second))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                }).autoDispose()
    }

    fun deleteItemRemote(itemId: String, onComplete: (AsyncResult<Unit>) -> Unit) {
        boardDataSource.deleteItem(itemId, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(Unit))
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
    }

    fun createEmptyItem(itemType: Int): BoardItem {
        val now = Date()
        val owners = ArrayList<String>().apply {
            userInteractor.getCurrentUserId()?.let (::add)
        }
        return when (itemType) {
            BoardItem.TYPE_EVENT -> EventItem(BoardItem.TYPE_EVENT, generateItemId(), now.time, now.time, owners,
                    EventInfo("", null, null, null, null,
                            "Asia/Bangkok", false, null, false, false, owners),
                    now)
            else -> TODO()
        }
    }

    fun createItem(item: BoardItem, onComplete: ((AsyncResult<BoardItem>) -> Unit)) {
        boardDataSource.createItem(item, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(item))
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
    }

    fun hasPlaceInfo(item: BoardItem): Boolean {
        return item.itemInfo is EventInfo && (item.itemInfo as? EventInfo)?.location?.googlePlaceId != null
                && TextUtils.isEmpty((item.itemInfo as? EventInfo)?.location?.name)
    }

    /**
     * Loads place info for board items that have a Google PlaceId associated
     *
     * @param itemIdsWithPlace    Board item IDs that contain Google Place IDs.
     *                  if NULL or empty, the algorithm will find items that contain Google Place IDs automatically.
     *                  If specified, it will use that instead.
     */
    fun loadItemPlaceInfo(placeProvider: PlaceProvider?, itemIdsWithPlace: List<String>?,
                          onComplete: (AsyncResult<androidx.collection.ArrayMap<String, Place>>) -> Unit) {
        if (placeProvider == null) {
            onComplete(AsyncErrorResult(Exception("Place provider implmentation is NULL")))
            return
        }
        val itemIds = ArrayList<String>()   // list to store item IDs that have Google Place IDs

        // first, we filter out all the board items that have a Google Place IDs in them.
        // they will be stored in itemIds
        // this callable will return filtered items as an array of item IDs
        Single.fromCallable {
            // if item IDs not provided, take all the loaded board items as starting point
            val items = if (itemIdsWithPlace.isNullOrEmpty()) {
                getCurrentBoard().items.takeLast(itemsLoaded)
            }

            // if item IDs are specified, make sure that the starting list contains valid item IDs
            else {
                itemIdsWithPlace!!.map { itemId ->
                    getCurrentBoard().items.find { it.itemId == itemId }!!
                }
            }

            // filter from the starting list the items that actually have Google Place IDs
            items.filter { item ->
                val hasPlaceInfo = hasPlaceInfo(item)
                if (hasPlaceInfo) {
                    itemIds.add(item.itemId)
                }
                hasPlaceInfo
            }.map {
                (it.itemInfo as EventInfo).location?.googlePlaceId!!
            }.toTypedArray()
        }.flatMap {
            placeProvider.getPlaces(it)
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (itemIds.size == it.size) {
                        onComplete(AsyncSuccessResult(androidx.collection.ArrayMap<String, Place>().apply {
                            for (i in itemIds.indices) {
                                put(itemIds[i], it[i])
                            }
                        }))
                    }
                    else {
                        onComplete(AsyncErrorResult(Exception("Failed to process result because" +
                                " returned places array size (${it.size}) does not match requested item array size (${itemIds.size})")))
                    }
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
    }

    /**
     * Returns a board item from cache, if available from specified item ID
     */
    fun getBoardItem(itemId: String?): BoardItem? {
        itemId ?: return null
        return getCurrentBoard().items.find { it.itemId == itemId }
    }

    //endregion
    //region private functions

    private fun getCurrentBoard(): Board = boardDataSource.getBoard(circleInteractor.getActiveCircleId(), itemType).blockingFirst()

    /**
     * Modified the board items arrangment, returning a Flowable in which items are grouped based on the defined filtering type.
     * This function call should be run in a background thread.
     */
    private fun processItems(board: Board): Flowable<Pair<Board, androidx.collection.ArrayMap<*, List<BoardItem>>>> {
        val map = when (itemFilterType) {
            ItemFilterType.EVENTS_BY_WEEK -> {
                androidx.collection.ArrayMap<Int?, List<BoardItem>>().apply {
                    if (board.items.isEmpty()) put(null, board.items)

                    val now = Calendar.getInstance().apply { time = Date() }
                    board.items.filter { it is EventItem }                              // make sure that all items are event items
                            .sortedBy { (it as EventItem).itemInfo.startTime }          // then sort by start time
                            .groupBy { item ->                                          // group by the week of year
                                Calendar.getInstance().let {
                                    val startTime = (item as EventItem).itemInfo.startTime
                                    if (startTime == null) null else {
                                        it.time = Date(startTime)
                                        when {
                                            now[Calendar.YEAR] > it[Calendar.YEAR] -> {

                                                // special case where year difference is 1
                                                if (now[Calendar.YEAR] - it[Calendar.YEAR] == 1 && it[Calendar.WEEK_OF_YEAR] == 1) {
                                                    (now[Calendar.WEEK_OF_YEAR] - 1) * -1
                                                }
                                                else {
                                                    (now[Calendar.WEEK_OF_YEAR] + ((BoardViewModel.NUM_WEEK_IN_YEAR
                                                            * (now[Calendar.YEAR] - it[Calendar.YEAR])) - it[Calendar.WEEK_OF_YEAR])) * -1
                                                }
                                            }
                                            now[Calendar.YEAR] < it[Calendar.YEAR] -> {
                                                ((BoardViewModel.NUM_WEEK_IN_YEAR * (it[Calendar.YEAR] - now[Calendar.YEAR]))
                                                        + it[Calendar.WEEK_OF_YEAR]) - now[Calendar.WEEK_OF_YEAR]
                                            }
                                            else -> {

                                                // for special case where the day falls in the first week of next year
                                                if (it[Calendar.WEEK_OF_YEAR] < now[Calendar.WEEK_OF_YEAR] && it[Calendar.WEEK_OF_YEAR] == 1) {
                                                    (BoardViewModel.NUM_WEEK_IN_YEAR + 1) - now[Calendar.WEEK_OF_YEAR]
                                                }

                                                else it[Calendar.WEEK_OF_YEAR] - now[Calendar.WEEK_OF_YEAR]
                                            }
                                        }
                                    }
                                }
                            }
                            .forEach { put(it.key, it.value) }
                }
            }
            else -> {
                androidx.collection.ArrayMap<Int?, List<BoardItem>>().apply {
                    put(null, ArrayList())
                }
            }
        }
        return Flowable.just(board to map)
    }

    //TODO need to standardize how to generate this ID with the server
    private fun generateItemId(): String {
        return UUID.randomUUID().toString()
    }

    private fun testParcelable(board: Board) {
        val parcel = Parcel.obtain()
        board.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val parceledBoard = Board.createFromParcel(parcel)
        for (i in board.items.indices) {
            val item = board.items[i]
            val parceledItem = parceledBoard.items[i]
            if (item == parceledItem) println("equal") else println("unequal")
        }
        parcel.recycle()
    }

    //endregion
}