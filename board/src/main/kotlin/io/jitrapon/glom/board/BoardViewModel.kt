package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.support.v4.util.ArrayMap
import android.support.v7.util.DiffUtil
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.Format
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.base.viewmodel.runAsync
import java.util.*
import kotlin.math.absoluteValue

/**
 * ViewModel class responsible for showing and interacting with the Board
 *
 * Created by Jitrapon on 11/18/2017.
 */
class BoardViewModel : BaseViewModel() {

    /* live data for the board items */
    private val observableBoard = MutableLiveData<BoardUiModel>()
    private val boardUiModel = BoardUiModel()

    /* live event for full-screen animation */
    private val observableAnimation = LiveEvent<AnimationItem>()

    /* live data for selected board item */
    private val observableSelectedBoardItem = MutableLiveData<BoardItem>()

    /* interactor for the observable board */
    private lateinit var interactor: BoardInteractor

    /* whether or not first load function has been called */
    private var firstLoadCalled: Boolean = false

    /* date time formatter */
    private val format: Format = Format()

    /* default filtering type of items */
    private var itemFilterType: ItemFilterType = ItemFilterType.EVENTS_BY_WEEK

    /* last grouping key that was emitted from the interactor. This is to indicate
        whether or not to append a new list of items to the last group, or to start a new group.
     */
    private var lastKeyGroup: Any? = null

    companion object {

        const val NUM_WEEK_IN_YEAR = 52
        const val FIRST_LOAD_ANIM_DELAY = 0L
        const val SUBSEQUENT_LOAD_ANIM_DELAY = 300L
    }

    init {
        interactor = BoardInteractor().apply {
            setFilteringType(itemFilterType)
        }
        loadBoard()
    }

    //region view actions

    /**
     * Loads board data and items asynchronously
     */
    fun loadBoard() {
        observableBoard.value = boardUiModel.apply {
            status = UiModel.Status.LOADING
        }

        runBlockingIO(interactor::loadBoard, if (!firstLoadCalled) FIRST_LOAD_ANIM_DELAY else SUBSEQUENT_LOAD_ANIM_DELAY) {
            when (it) {
                is AsyncSuccessResult -> {
                    runAsync({
                        it.result.toUiModel().let {
                            it to if (boardUiModel.items.isNullOrEmpty()) null else
                                DiffUtil.calculateDiff(BoardItemDiffCallback(boardUiModel.items, it), true)
                        }
                    }, onComplete = { (uiModel, diff) ->
                        observableBoard.value = boardUiModel.apply {
                            itemsChangedIndices = null
                            status = if (uiModel.isEmpty()) UiModel.Status.EMPTY else UiModel.Status.SUCCESS
                            items = uiModel
                            diffResult = diff
                            shouldLoadPlaceInfo = true
                        }
                    }, onError = {
                        handleError(it)
                        observableBoard.value = boardUiModel.apply {
                            status = UiModel.Status.ERROR
                            items = null
                        }
                    })
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
        firstLoadCalled = true
    }

    /**
     * Retrieves place information for board items that need to show them.
     *
     * @param placeProvider Subclass of PlaceProvider that implements get place info
     */
    fun loadPlaceInfo(placeProvider: PlaceProvider?) {
        interactor.loadItemPlaceInfo(placeProvider) {
            when (it) {
                is AsyncSuccessResult -> {
                    if (!it.result.isEmpty) {
                        observableBoard.value = boardUiModel.apply {
                            shouldLoadPlaceInfo = false
                            itemsChangedIndices = ArrayList()
                            diffResult = null
                            val map = it.result
                            items?.let {
                                it.forEachIndexed { index, item ->
                                    if (map.containsKey(item.itemId)) {
                                        itemsChangedIndices?.add(index to arrayListOf(EventItemUiModel.LOCATION))
                                        if (item is EventItemUiModel) {
                                            item.location = AndroidString(text = map[item.itemId]?.name.toString())
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
     * Expands current board item (specified by position in the recyclerview)
     *
     * @param position The position of the item to view in the RecyclerView
     */
    fun selectItem(position: Int) {
        boardUiModel.items?.getOrNull(position)?.let {
            interactor.getBoardItem(it.itemId)?.let {
                observableSelectedBoardItem.value = it
            }
        }
    }

    //endregion
    //region util functions

    /**
     * Converts the BoardItem domain model to a list of BoardItemUIModel
     */
    private fun ArrayMap<*, List<BoardItem>>.toUiModel(): List<BoardItemUiModel> {
        if (isEmpty) return ArrayList()

        // every key in this map represents an information about a group's heading
        // however if there is only one key, we don't have to group any items
        if (keys.size == 1) {
            return this[keyAt(0)]!!.map { it.toUiModel() }
        }

        val map = this
        return ArrayList<BoardItemUiModel>().apply {
            for ((keyIndex, key) in keys.withIndex()) {
                if (keyIndex == map.size - 1) lastKeyGroup = key
                add(HeaderItemUiModel(AndroidString(
                        resId = when {
                            (key is Int && key < -1) -> { R.string.event_card_header_last_n_weeks }
                            (key is Int && key == -1) -> { R.string.event_card_header_last_week }
                            key == null -> R.string.board_item_header_no_date
                            (key is Int && key == 0) -> R.string.board_item_header_this_week
                            (key is Int && key == 1) -> R.string.board_item_header_next_week
                            (key is Int && key > 1) -> R.string.board_item_header_other_weeks
                            else -> R.string.board_item_header_undefined
                        },
                        formatArgs = if (key == null) null else {
                            if (key is Int) {
                                if (key > 1 || key < -1) arrayOf(key.absoluteValue.toString()) else null
                            }
                            else null
                        }
                )))
                map[key]?.let { items ->
                    items.forEach { item ->
                        add(item.toUiModel())
                    }
                }
            }
        }
    }

    private fun BoardItem.toUiModel(): BoardItemUiModel {
        return when (this) {
            is EventItem -> {
                EventItemUiModel(
                        itemId,
                        itemInfo.eventName,
                        getEventDateRange(itemInfo.startTime, itemInfo.endTime),
                        getEventLocation(itemInfo.location),
                        getEventLatLng(itemInfo.location),
                        getEventAttendees(itemInfo.attendees),
                        getEventAttendStatus(itemInfo.attendees)
                )
            }
            else -> {
                ErrorItemUiModel()
            }
        }
    }

    // region EventItem to EventItemUiModel util functions

    /**
     * Returns a formatted date range
     */
    private fun getEventDateRange(start: Long?, end: Long?): String? {
        start ?: return null

        // if end datetime is not present, only show start time (i.e. 20 Jun, 2017 (10:30 AM))
        if (end == null) return "${format.date(start, Format.DEFAULT_DATE_FORMAT)} (${format.time(start)})"

        // if end datetime is present
        // show one date with time range if end datetime is within the same day
        // (i.e. 20 Jun, 2017 (10:30 AM - 3:00 PM), otherwise
        // show 20 Jun, 2017 (10:30 AM) - 21 Jun, 2017 (10:30 AM)
        val startDate = Calendar.getInstance().apply { time = Date(start) }
        val endDate = Calendar.getInstance().apply { time = Date(end) }
        return if (startDate[Calendar.YEAR] < endDate[Calendar.YEAR] ||
                startDate[Calendar.DAY_OF_YEAR] < endDate[Calendar.DAY_OF_YEAR]) {
            "${format.date(start, Format.DEFAULT_DATE_FORMAT)} (${format.time(start)}) " +
                    "- ${format.date(end, Format.DEFAULT_DATE_FORMAT)} (${format.time(end)})"
        }
        else {
            "${format.date(start, Format.DEFAULT_DATE_FORMAT)} (${format.time(start)} - ${format.time(end)})"
        }
    }

    /**
     * Returns location string from EventLocation. If the location has been loaded before,
     * retrieve it from the cache, otherwise asynchronously call the respective API
     * to retrieve location data
     */
    private fun getEventLocation(location: EventLocation?): AndroidString? {
        location ?: return null
        if (location.placeId == null && location.googlePlaceId == null) {
            return AndroidString(resId = R.string.event_card_location_latlng,
                    formatArgs = arrayOf(location.latitude?.toString() ?: "null", location.longitude?.toString() ?: "null"))
        }
        return AndroidString(R.string.event_card_location_placeholder)
    }

    /**
     * Returns a latlng corresponding to the location of the event
     */
    private fun getEventLatLng(location: EventLocation?): LatLng? {
        location ?: return null
        if (location.latitude != null && location.longitude != null) {
            return LatLng(location.latitude, location.longitude)
        }
        return null
    }

    /**
     * Returns the list of user avatars from user ids
     */
    private fun getEventAttendees(userIds: List<String>?): MutableList<String?>? {
        userIds ?: return null
        val users = interactor.getUsers(userIds) ?: return null
        return users.map { it?.avatar }.toMutableList()
    }

    /**
     * Returns a AttendStatus from the list of attending userIds
     */
    private fun getEventAttendStatus(userIds: List<String>): EventItemUiModel.AttendStatus {
        return if (userIds.any { it.equals(interactor.getCurrentUser()?.userId, true) })  EventItemUiModel.AttendStatus.GOING
        else EventItemUiModel.AttendStatus.MAYBE
    }

    /**
     * Change current attend status of the user id on a specified event
     */
    fun setEventAttendStatus(position: Int, newStatus: EventItemUiModel.AttendStatus) {
        boardUiModel.items?.let { items ->
            val item = items.getOrNull(position)
            if (item is EventItemUiModel) {
                val statusCode: Int
                val animationItem: AnimationItem
                val message: AndroidString
                var level: Int = MessageLevel.INFO
                when (newStatus) {
                    EventItemUiModel.AttendStatus.GOING -> {
                        statusCode = 2
                        animationItem = AnimationItem.JOIN_EVENT
                        message = AndroidString(R.string.event_card_join_success, arrayOf(item.title))
                        level = MessageLevel.SUCCESS
                    }
                    EventItemUiModel.AttendStatus.MAYBE -> {
                        statusCode = 1
                        animationItem = AnimationItem.DECLINE_EVENT
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title))
                    }
                    EventItemUiModel.AttendStatus.DECLINED -> {
                        statusCode = 0
                        animationItem = AnimationItem.DECLINE_EVENT
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title))
                    }
                }
                item.apply {
                    attendStatus = newStatus
                }
                observableBoard.value = boardUiModel.apply {
                    shouldLoadPlaceInfo = false
                    diffResult = null
                    itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to arrayListOf(EventItemUiModel.ATTENDSTATUS)) }
                }
                observableAnimation.value = animationItem
                interactor.markEventAttendStatusForCurrentUser(item.itemId, statusCode) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            item.apply {
                                attendeesAvatars = getEventAttendees(it.result)
                            }
                            observableBoard.value = boardUiModel.apply {
                                shouldLoadPlaceInfo = false
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to
                                        arrayListOf(EventItemUiModel.ATTENDSTATUS, EventItemUiModel.ATTENDEES)) }
                            }
                            observableViewAction.value = Snackbar(message, level = level)
                        }
                        is AsyncErrorResult -> {
                            handleError(it.error)
                        }
                    }
                }
            }
        }
    }

    //endregion

    /**
     * Clean up any resources
     */
    override fun onCleared() {
        //nothing yet
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
    fun getObservableAnimation(): LiveEvent<AnimationItem> = observableAnimation

    /**
     * Returns an observable that if set, becomes the currently selected item
     */
    fun getObservableSelectedBoardItem(): LiveData<BoardItem> = observableSelectedBoardItem

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