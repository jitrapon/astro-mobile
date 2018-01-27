package io.jitrapon.glom.board.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.Format
import io.jitrapon.glom.board.*
import java.util.*

/**
 * Manages all view states for event board items and detail screen
 *
 * Created by Jitrapon
 */
class EventItemViewModel : BoardItemViewModel() {

    private lateinit var interactor: EventItemInteractor

    private var prevName: String? = null
    private val observableName = MutableLiveData<String>()

    /* indicates whether or not the view should display autocomplete */
    private var shouldShowNameAutocomplete: Boolean = true

    init {
        interactor = EventItemInteractor()
    }

    //region event board item

    override fun toUiModel(item: BoardItem, status: UiModel.Status): BoardItemUiModel {
        return (item as EventItem).let {
            EventItemUiModel(
                    it.itemId,
                    it.itemInfo.eventName,
                    getEventDateRange(it.itemInfo.startTime, it.itemInfo.endTime),
                    getEventLocation(it.itemInfo.location),
                    getEventLatLng(it.itemInfo.location),
                    getEventAttendees(it.itemInfo.attendees),
                    getEventAttendStatus(it.itemInfo.attendees),
                    status = status
            )
        }
    }

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
    fun setEventAttendStatus(boardViewModel: BoardViewModel, position: Int, newStatus: EventItemUiModel.AttendStatus) {
        boardViewModel.boardUiModel.items?.let { items ->
            val item = items.getOrNull(position)
            if (item is EventItemUiModel) {
                val statusCode: Int
                var animationItem: AnimationItem? = null
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
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title))
                    }
                    EventItemUiModel.AttendStatus.DECLINED -> {
                        statusCode = 0
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title))
                    }
                }
                item.apply {
                    attendStatus = newStatus
                }
                boardViewModel.observableBoard.value = boardViewModel.boardUiModel.apply {
                    requestPlaceInfoItemIds = null
                    diffResult = null
                    itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to arrayListOf(EventItemUiModel.ATTENDSTATUS)) }
                }
                animationItem?.let {
                    boardViewModel.observableAnimation.value = it
                }
                interactor.markEventAttendStatusForCurrentUser(item.itemId, statusCode) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            item.apply {
                                attendeesAvatars = getEventAttendees(it.result)
                            }
                            boardViewModel.observableBoard.value = boardViewModel.boardUiModel.apply {
                                requestPlaceInfoItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to
                                        arrayListOf(EventItemUiModel.ATTENDSTATUS, EventItemUiModel.ATTENDEES)) }
                            }
                            boardViewModel.observableViewAction.value = Snackbar(message, level = level)
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

    //region event detail item

    fun setPlaceProvider(placeProvider: PlaceProvider) {
        interactor.initializeNameAutocompleter(placeProvider)
    }

    /**
     * Initializes this ViewModel with the event item to display
     */
    fun setItem(item: BoardItem?) {
        item.let {
            if (it == null) {
                AppLogger.w("Cannot set item because item is NULL")
            }
            else {
                if (item is EventItem) {
                    interactor.setItem(item)
                    prevName = item.itemInfo.eventName
                    observableName.value = item.itemInfo.eventName
                }
                else {
                    AppLogger.w("Cannot set item because item is not an instance of EventItem")
                }
            }
        }
    }

    /**
     * Returns whether or not autocomplete on the name text should be enabled
     */
    fun shouldShowNameAutocomplete(): Boolean = shouldShowNameAutocomplete

    /**
     * Updates the current even name, should be called after each character update
     */
    fun onNameChanged(string: String) {
        interactor.onNameChanged(string)
    }

    /**
     * Saves the current state and returns a model object with the state
     */
    fun saveAndGetItem(name: String): BoardItem? {
        val item = interactor.getItem()
        return (item?.itemInfo as? EventInfo)?.let {
            it.eventName = name
            interactor.saveItem(it)
            item
        }
    }

    //endregion
    //region view states

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = false

    /**
     * Returns the event name before change
     */
    fun getPreviousName(): String? = prevName

    //endregion
    //region observables

    fun getObservableName(): LiveData<String> = observableName

    //endregion

    override fun cleanUp() {
        //nothing yet
    }
}