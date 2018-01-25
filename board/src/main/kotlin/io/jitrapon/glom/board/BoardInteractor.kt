package io.jitrapon.glom.board

import android.os.Parcel
import android.support.v4.util.ArrayMap
import android.text.TextUtils
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.User
import io.jitrapon.glom.base.repository.UserRepository
import io.jitrapon.glom.board.event.EventInfo
import io.jitrapon.glom.board.event.EventItem
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Interactor for dealing with Board logic
 *
 * @author Jitrapon Tiachunpun
 */
class BoardInteractor {

    /*
     * Repository retrieving and saving board data
     */
    private lateinit var boardRepository: BoardRepository

    /*
     * Repository retrieving and saving user data
     */
    private lateinit var userRepository: UserRepository

    /*
     * The number of items that was loaded
     */
    private var itemsLoaded: Int = 0

    /*
     * Filtering type of items
     */
    private var itemFilterType: ItemFilterType = ItemFilterType.EVENTS_BY_WEEK

    init {
        boardRepository = BoardRepository()
        userRepository = UserRepository()
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
    fun loadBoard(onComplete: (AsyncResult<ArrayMap<*, List<BoardItem>>>) -> Unit) {
        Flowable.zip(boardRepository.load(), userRepository.loadList(),
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
                    onComplete(AsyncSuccessResult(it.second))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                })
    }

    /**
     * Modified the board items arrangment, returning a Flowable in which items are grouped based on the defined filtering type.
     * This function call should be run in a background thread.
     */
    private fun processItems(board: Board): Flowable<Pair<Board, ArrayMap<*, List<BoardItem>>>> {
        val map = when (itemFilterType) {
            ItemFilterType.EVENTS_BY_WEEK -> {
                ArrayMap<Int?, List<BoardItem>>().apply {
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
                                                (now[Calendar.WEEK_OF_YEAR] + ((BoardViewModel.NUM_WEEK_IN_YEAR
                                                        * (now[Calendar.YEAR] - it[Calendar.YEAR])) - it[Calendar.WEEK_OF_YEAR])) * -1
                                            }
                                            now[Calendar.YEAR] < it[Calendar.YEAR] -> {
                                                ((BoardViewModel.NUM_WEEK_IN_YEAR * (it[Calendar.YEAR] - now[Calendar.YEAR]))
                                                        + it[Calendar.WEEK_OF_YEAR]) - now[Calendar.WEEK_OF_YEAR]
                                            }
                                            else -> {

                                                // for special case where the day falls in the first week of next year
                                                if (it[Calendar.WEEK_OF_YEAR] < now[Calendar.WEEK_OF_YEAR] && it[Calendar.WEEK_OF_YEAR] == 1) {
                                                    (BoardViewModel.NUM_WEEK_IN_YEAR + it[Calendar.WEEK_OF_YEAR]) - now[Calendar.WEEK_OF_YEAR]
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
        return Flowable.just(board to map)
    }

    /**
     * Loads place info for board items that have placeId associated
     */
    fun loadItemPlaceInfo(placeProvider: PlaceProvider?, onComplete: (AsyncResult<ArrayMap<String, Place>>) -> Unit) {
        placeProvider ?: return
        val itemIds = ArrayList<String>()      // list to store item IDs that have Google Place IDs
        boardRepository.getCache()?.let {
            Single.fromCallable {
                it.items.takeLast(itemsLoaded)          // only load place info for items that are loaded in the last page
                        .filter { item ->
                            (item.itemInfo is EventInfo && (item.itemInfo as? EventInfo)?.location?.googlePlaceId != null).let {
                                if (it) itemIds.add(item.itemId)
                                it
                            }
                        }
                        .map { (it.itemInfo as EventInfo).location?.googlePlaceId!! }.toTypedArray()
            }.flatMap {
                placeProvider.retrievePlaces(it)
            }.subscribeOn(Schedulers.io())
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
                            onComplete(AsyncErrorResult(Exception("Failed to process result because" +
                                    " returned places array size (${it.size}) does not match requested item array size (${itemIds.size})")))
                        }
                    }, {
                        onComplete(AsyncErrorResult(it))
                    })
        }
    }

    /**
     * Returns list of loaded users, if available from specified IDs
     */
    fun getUsers(userIds: List<String>): List<User?>? {
        return ArrayList<User?>().apply {
            userIds.forEach {
                add(userRepository.getById(it))
            }
        }
    }

    /**
     * Returns a board item from cache, if available from specified item ID
     */
    fun getBoardItem(itemId: String?): BoardItem? {
        itemId ?: return null
        return boardRepository.getCache()?.items?.find { it.itemId == itemId }
    }

    /**
     * Joins the current user to an event
     *
     * @param statusCode - An int value for the new status (0 for DECLINED, 1 for MAYBE, 2 for GOING)
     */
    fun markEventAttendStatusForCurrentUser(itemId: String?, statusCode: Int, onComplete: ((AsyncResult<MutableList<String>?>) -> Unit)) {
        if (itemId == null) {
            onComplete(AsyncErrorResult(Exception("ItemId cannot be NULL")))
            return
        }
        val userId = userRepository.getCurrentUser()?.userId
        if (TextUtils.isEmpty(userId)) {
            onComplete(AsyncErrorResult(Exception("Current user id cannot be NULL")))
            return
        }

        boardRepository.getCache()?.let {
            Flowable.fromCallable {
                it.items.find { it.itemId == itemId && it is EventItem }
            }.flatMap {
                when (statusCode) {
                    2 -> boardRepository.joinEvent(userId!!, it.itemId)
                    else -> boardRepository.declineEvent(userId!!, it.itemId)
                }
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        onComplete(AsyncSuccessResult(it.attendees))
                    }, {
                        onComplete(AsyncErrorResult(it))
                    }, {
                        //nothing yet
                    })
        }
    }

    /**
     * Returns the currently signed in User object
     */
    fun getCurrentUser(): User? {
        return userRepository.getCurrentUser()
    }

    /**
     * Edits this board item with a new info
     */
    fun editBoardItemInfo(item: BoardItem, onComplete: ((AsyncResult<BoardItem>) -> Unit)) {
        boardRepository.editBoardItemInfo(item)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(it))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                })
    }

    private fun testParcelable(board: Board) {
        val parcel = Parcel.obtain()
        board.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val parceledBoard = Board.CREATOR.createFromParcel(parcel)
        for (i in board.items.indices) {
            val item = board.items[i]
            val parceledItem = parceledBoard.items[i]
            if (item == parceledItem) println("equal") else println("unequal")
        }
        parcel.recycle()
    }

    fun getCachedBoard(): Board? = boardRepository.getCache()
}