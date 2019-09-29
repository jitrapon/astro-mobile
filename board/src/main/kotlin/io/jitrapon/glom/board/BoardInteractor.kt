package io.jitrapon.glom.board

import android.annotation.SuppressLint
import android.location.Address
import android.os.Parcel
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.collection.ArrayMap
import com.google.android.libraries.places.api.model.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.domain.circle.Circle
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.User
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventInfo
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventSource
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Interactor for dealing with Board business logic
 *
 * @author Jitrapon Tiachunpun
 */
class BoardInteractor(
    private val userInteractor: UserInteractor, private val boardDataSource: BoardDataSource,
    private val circleInteractor: CircleInteractor
) : BaseInteractor() {

    /*
     * The number of items that was loaded
     */
    private var itemsLoaded: Int = 0

    /*
     * Board item types
     */
    var itemType: Int = BoardItem.TYPE_EVENT

    /*
     * Filtering type of items
     */
    var itemFilterType: ItemFilterType = ItemFilterType.EVENTS_BY_WEEK

    /*
     * The currently active circle ID
     */
    private val circleId: String
        get() = circleInteractor.getActiveCircleId()

    /*
     * Callback to notify an observer that the data may require refresh
     * True iff data should be refreshed
     */
    var onDataChange: ((AsyncResult<Boolean>) -> Unit)? = null
        set(value) {
            field = value
            field?.let {
                subscribeToContentChange(it)
            }
        }

    //region public functions

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
    fun loadBoard(
        refresh: Boolean,
        onComplete: (AsyncResult<Pair<Date, ArrayMap<*, List<BoardItem>>>>) -> Unit
    ) {
        Flowable.zip(boardDataSource.getBoard(
            circleId,
            itemType,
            refresh
        ).subscribeOn(Schedulers.io()),
            userInteractor.getUsers(circleId, refresh).subscribeOn(Schedulers.io()),
            circleInteractor.loadCircle(refresh).subscribeOn(Schedulers.io()),                      // must subscribe to achieve true parallelism
            Function3<Board, List<User>, Circle, Triple<Board, List<User>, Circle>> { board, users, circle ->
                Triple(board, users, circle)
            })
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .flatMap {
                processItems(it.first)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                itemsLoaded = it.first.items.size
            }
            .measureExecutionTime("BoardInteractor#loadBoard")
            .subscribe({
                onComplete(AsyncSuccessResult(it.first.retrievedTime!! to it.second))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //not applicable
            }).autoDispose()
    }

    /**
     * Adds a new board item to the list, and process it to be grouped appropriately
     */
    fun addItem(item: BoardItem, onComplete: (AsyncResult<ArrayMap<*, List<BoardItem>>>) -> Unit) {
        boardDataSource.createItem(circleId, item, false)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .andThen(
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
        val completable = when {
            item.isSyncingToRemote -> Completable.mergeArray(
                boardDataSource.deleteItem(
                    circleId,
                    item.itemId,
                    false
                ).subscribeOn(Schedulers.io()),
                (boardDataSource.createItem(circleId, item, false).andThen(
                    boardDataSource.createItem(
                        circleId,
                        item,
                        true
                    )
                ).subscribeOn(Schedulers.io()))
            )
            item.isSyncingToLocal -> Completable.mergeArray(
                boardDataSource.deleteItem(
                    circleId,
                    item.itemId,
                    true
                ).subscribeOn(Schedulers.io()),
                boardDataSource.editItem(circleId, item, true).subscribeOn(Schedulers.io())
            )
            else -> boardDataSource.editItem(circleId, item, true)
        }
        completable
            .retryWhen(::errorIsUnauthorized)
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
    fun deleteItemLocal(
        itemId: String,
        onComplete: (AsyncResult<ArrayMap<*, List<BoardItem>>>) -> Unit
    ) {
        boardDataSource.deleteItem(circleId, itemId, false)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .andThen(
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
        boardDataSource.deleteItem(circleId, itemId, true)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun createEmptyItem(): BoardItem {
        val now = Date()
        val owners = ArrayList<String>().apply {
            userId?.let(::add)
        }
        return when (itemType) {
            BoardItem.TYPE_EVENT -> EventItem(
                BoardItem.TYPE_EVENT, generateItemId(), now.time, now.time, owners,
                EventInfo(
                    "", null, null, null, null,
                    "Asia/Bangkok", false, null,
                    datePollStatus = false,
                    placePollStatus = false,
                    attendees = owners,
                    source = EventSource(null, null, null, circleInteractor.getActiveCircleId())
                ),
                true, SyncStatus.OFFLINE, now
            )
            else -> TODO()
        }
    }

    fun createItem(item: BoardItem, onComplete: ((AsyncResult<BoardItem>) -> Unit)) {
        if (item.isSyncable) {
            boardDataSource.createItem(circleId, item, true)
                .retryWhen(::errorIsUnauthorized)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(item))
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
        }
        else {
            onComplete(AsyncSuccessResult(item))
        }
    }

    fun hasPlaceInfo(item: BoardItem): Boolean {
        return item.itemInfo is EventInfo && (item.itemInfo as? EventInfo)?.location?.googlePlaceId != null
                && TextUtils.isEmpty((item.itemInfo as? EventInfo)?.location?.name)
    }

    private fun hasOnlyLocationName(item: BoardItem): Boolean {
        return item.itemInfo is EventInfo &&
                !(item.itemInfo as? EventInfo)?.location?.name.isNullOrBlank() &&
                (item.itemInfo as? EventInfo)?.location?.googlePlaceId == null
    }

    /**
     * Loads place info for board items that have a Google PlaceId associated
     *
     * @param itemIdsWithPlace    Board item IDs that contain Google Place IDs.
     *                  if NULL or empty, the algorithm will find items that contain Google Place IDs automatically.
     *                  If specified, it will use that instead.
     */
    fun loadItemPlaceInfo(
        placeProvider: PlaceProvider?, itemIdsWithPlace: List<String>?,
        onComplete: (AsyncResult<ArrayMap<String, Place>>) -> Unit
    ) {
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
        }.retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (itemIds.size == it.size) {
                    onComplete(AsyncSuccessResult(ArrayMap<String, Place>().apply {
                        for (i in itemIds.indices) {
                            put(itemIds[i], it[i])
                        }
                    }))
                }
                else {
                    onComplete(
                        AsyncErrorResult(
                            Exception(
                                "Failed to process result because" +
                                        " returned places array size (${it.size}) does not match requested item array size (${itemIds.size})"
                            )
                        )
                    )
                }
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    /**
     * Loads addresses info for board items that have a location name, but no Google Place IDs or LatLng.
     */
    fun loadItemAddressInfo(
        placeProvider: PlaceProvider?,
        itemIdsWithPlace: List<String>?,
        onComplete: (AsyncResult<Map<String, Pair<String?, Address?>>>) -> Unit
    ) {
        if (placeProvider == null) {
            onComplete(AsyncErrorResult(Exception("Place provider implmentation is NULL")))
            return
        }
        val queryMap = ArrayMap<String, String>()   // list to store item IDs and queries

        // first, we filter out all the board items that have location names in them.
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

            // filter from the starting list the items that actually have location names
            // with no Place IDs
            items.filter { item ->
                val hasOnlyLocationName = hasOnlyLocationName(item)
                if (hasOnlyLocationName) {
                    val query = (item.itemInfo as EventInfo).location!!.name
                    queryMap[query] = item.itemId
                }
                hasOnlyLocationName
            }.map {
                (it.itemInfo as EventInfo).location?.name!!
            }.toList()
        }.flatMap {
            placeProvider.geocode(it)
        }.map {
            ArrayMap<String, Pair<String?, Address?>>().apply {
                for ((query, address) in it.entries) {
                    this[queryMap[query]] = query to address
                }
            }
        }.retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
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

    private fun getCurrentBoard(): Board =
        boardDataSource.getBoard(circleInteractor.getActiveCircleId(), itemType).blockingFirst()

    /**
     * Modified the board items arrangment, returning a Flowable in which items are grouped based on the defined filtering type.
     * This function call should be run in a background thread.
     */
    @WorkerThread
    private fun processItems(board: Board): Flowable<Pair<Board, ArrayMap<*, List<BoardItem>>>> {
        return Flowable.fromCallable {
            val currentTime = System.currentTimeMillis()

            val map = when (itemFilterType) {
                ItemFilterType.EVENTS_BY_WEEK -> {
                    ArrayMap<Int?, List<BoardItem>>().apply {
                        if (board.items.isEmpty()) put(null, board.items)

                        val now = Calendar.getInstance().apply { time = Date() }
                        board.items.filterIsInstance<EventItem>()                              // make sure that all items are event items
                            .sortedBy { it.itemInfo.startTime }          // then sort by start time
                            .groupBy { item ->
                                // group by the week of year
                                Calendar.getInstance().let {
                                    val startTime = item.itemInfo.startTime
                                    if (startTime == null) null
                                    else {
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
                    ArrayMap<Int?, List<BoardItem>>().apply {
                        put(null, ArrayList())
                    }
                }
            }
            AppLogger.d("BoardInteractor#processItems took ${System.currentTimeMillis() - currentTime} ms")
            board to map
        }
    }

    fun setItemSyncStatus(itemId: String, status: SyncStatus) {
        boardDataSource.setItemSyncStatus(itemId, status)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }

    @SuppressLint("CheckResult")
    private fun subscribeToContentChange(onChange: (AsyncResult<Boolean>) -> Unit) {
        if (!boardDataSource.contentChangeNotifier.hasObservers()) {
            boardDataSource.contentChangeNotifier.throttleFirst(1000L, TimeUnit.MILLISECONDS).doOnSubscribe {
                AppLogger.d("BoardDataSource's contentChangeNotifier is subscribed")
            }.subscribe({
                // this is invoked on a background thread
                AppLogger.d("BoardDataSource's contentChangeNotifier emits $it on thread ${Thread.currentThread().name}")
                val shouldRefreshAutomatically = !it.isRemote
                onChange.invoke(AsyncSuccessResult(shouldRefreshAutomatically))
            }, {
                // this is invoked on a background thread
                AppLogger.e("BoardDataSource's contentChangeNotifier emits $it on thread ${Thread.currentThread().name}")
                onChange.invoke(AsyncErrorResult(it))
            }, {
                AppLogger.d("Unsubscribe from contentChangeNotifier as it no longer emits any values")
            })
        }
    }

    override fun cleanup() {
        super.cleanup()
        boardDataSource.cleanUpContentChangeNotifier()
    }

    //TODO need to standardize how to generate this ID with the server
    private fun generateItemId(): String {
        return UUID.randomUUID().toString()
    }

    @Suppress("unused")
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
