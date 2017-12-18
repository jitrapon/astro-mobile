package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.support.v4.util.ArrayMap
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.util.Format
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.viewmodel.BaseViewModel
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
                    it.result.let {
                        observableBoard.value = boardUiModel.apply {
                            status = if (it.isEmpty) UiModel.Status.EMPTY else UiModel.Status.SUCCESS
                            items = it.toUiModel()
                            shouldLoadPlaceInfo = true
                            shouldLoadUserAvatars = true
                            itemsChangedIndices = null
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
        firstLoadCalled = true
    }

    /**
     * Retrieves place information for board items. We need this call from the View because
     * PlaceProvider can only be instantiated using the Android.view context
     */
    fun loadPlaceInfo(placeProvider: PlaceProvider?) {
        interactor.loadItemPlaceInfo(placeProvider) {
            when (it) {
                is AsyncSuccessResult -> {
                    observableBoard.value = boardUiModel.apply {
                        shouldLoadPlaceInfo = false
                        itemsChangedIndices = ArrayList()
                        val map = it.result
                        items?.let {
                            it.forEachIndexed { index, item ->
                                if (map.containsKey(item.itemId)) {
                                    itemsChangedIndices?.add(index)
                                    if (item is EventItemUiModel) {
                                        item.location = AndroidString(text = map[item.itemId]?.name.toString())
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

    //endregion
    //region util functions

    /**
     * Converts the BoardItem domain model to a list of BoardItemUIModel
     */
    private fun ArrayMap<*, List<BoardItem>>.toUiModel(): List<BoardItemUiModel> {
        if (isEmpty) return Collections.emptyList<BoardItemUiModel>()

        // every key in this map represents an information about a group's heading
        // however if there is only one key, we don't have to group any items
        if (keys.size == 1) {
            return this[0]!!.map { it.toUiModel() }
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
                        getDateRangeString(itemInfo.startTime, itemInfo.endTime),
                        getOrLoadLocationString(itemInfo.location),
                        getMapLatLng(itemInfo.location),
                        getUserAvatars(itemInfo.attendees)
                )
            }
            else -> {
                ErrorItemUiModel()
            }
        }
    }

    /**
     * Returns a formatted date range
     */
    private fun getDateRangeString(start: Long?, end: Long?): String? {
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
    private fun getOrLoadLocationString(location: EventLocation?): AndroidString? {
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
    private fun getMapLatLng(location: EventLocation?): LatLng? {
        location ?: return null
        if (location.latitude != null && location.longitude != null) {
            return LatLng(location.latitude, location.longitude)
        }
        return null
    }

    /**
     * Returns the list of user avatars from user ids
     */
    private fun getUserAvatars(userIds: List<String>): List<String?>? {
        val users = interactor.getUsers(userIds) ?: return null
        return users.map { it?.avatar }
    }

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