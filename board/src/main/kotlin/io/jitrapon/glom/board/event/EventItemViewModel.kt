package io.jitrapon.glom.board.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.TextUtils
import androidx.text.bold
import androidx.text.buildSpannedString
import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.*
import io.jitrapon.glom.board.Const.NAVIGATE_TO_EVENT_PLAN
import java.util.*
import javax.inject.Inject

/**
 * Manages all view states for event board items and detail screen
 *
 * Created by Jitrapon
 */
class EventItemViewModel : BoardItemViewModel() {

    @Inject
    lateinit var interactor: EventItemInteractor

    /* cache copy of the unmodified event name, used for displaying during the transition end animation */
    private var prevName: String? = null

    /* observable event name, containing values for the text to display
        and a Boolean for whether or not to filter autocomplete after text change */
    private val observableName = MutableLiveData<Pair<AndroidString, Boolean>>()

    /* observable start date */
    private val observableStartDate = MutableLiveData<AndroidString>()

    /* observable end date */
    private val observableEndDate = MutableLiveData<AndroidString>()

    /* observable model for the DateTime picker. When set if not null, show dialog */
    private val observableDateTimePicker = MutableLiveData<Pair<DateTimePickerUiModel, Boolean>>()

    /* indicates whether or not the view should display autocomplete */
    private var shouldShowNameAutocomplete: Boolean = true

    /* observable event location */
    private val observableLocation = MutableLiveData<AndroidString>()

    /* observable event location description */
    private val observableLocationDescription = MutableLiveData<AndroidString>()

    /* observable event location latlng */
    private val observableLocationLatLng = MutableLiveData<LatLng>()

    /* observable title for attendee */
    private val observableAttendeeTitle = MutableLiveData<AndroidString>()

    /* observable list of event attendees */
    private val observableAttendees = MutableLiveData<List<UserUiModel>>()

    /* observable button model for attend status */
    private val observableAttendStatus = MutableLiveData<ButtonUiModel>()

    /* cached attend status */
    private var attendStatus: EventItemUiModel.AttendStatus = EventItemUiModel.AttendStatus.MAYBE

    /* observable event note */
    private val observableNote = MutableLiveData<AndroidString?>()

    /* observable plan status */
    private val observablePlanStatus = MutableLiveData<ButtonUiModel>()

    /* observable flag to indicate that a navigation event should be triggered */
    private val observableNavigation = LiveEvent<Navigation>()

    /* whether or not this is a new item to add */
    private var isNewItem: Boolean = false

    init {
        BoardInjector.getComponent().inject(this)
    }

    //region event board item

    override fun toUiModel(item: BoardItem, status: UiModel.Status): BoardItemUiModel {
        return (item as EventItem).let {
            EventItemUiModel(
                    itemId = it.itemId,
                    title = AndroidString(text = it.itemInfo.eventName),
                    dateTime = getEventDate(it.itemInfo.startTime, it.itemInfo.endTime),
                    location = getEventLocation(it.itemInfo.location),
                    mapLatLng = getEventLatLng(it.itemInfo.location),
                    attendeesAvatars = getEventAttendees(it.itemInfo.attendees),
                    attendStatus = getEventAttendStatus(it.itemInfo.attendees),
                    status = status,
                    isPlanning = it.itemInfo.datePollStatus || it.itemInfo.placePollStatus
            )
        }
    }

    /**
     * Returns a formatted date range
     */
    private fun getEventDate(start: Long?, end: Long?): AndroidString? {
        start ?: return null
        val startDate = Calendar.getInstance().apply { time = Date(start) }
        val currentDate = Calendar.getInstance()
        var showYear = startDate[Calendar.YEAR] != currentDate[Calendar.YEAR]

        // if end datetime is not present, only show start time
        if (end == null) return Date(start).let {
            AndroidString(text = "${it.toDateString(showYear)} (${it.toTimeString()})")
        }

        // if end datetime is present
        // show one date with time range if end datetime is within the same day
        // (i.e. 20 Jun, 2017 (10:30 AM - 3:00 PM), otherwise
        // show 20 Jun, 2017 (10:30 AM) - 21 Jun, 2017 (10:30 AM)
        val endDate = Calendar.getInstance().apply { time = Date(end) }
        val startYearNotEqEndYear = startDate[Calendar.YEAR] != endDate[Calendar.YEAR]
        return if (startYearNotEqEndYear || startDate[Calendar.DAY_OF_YEAR] != endDate[Calendar.DAY_OF_YEAR]) {
            showYear = showYear || startYearNotEqEndYear
            AndroidString(text = StringBuilder().apply {
                append(Date(start).let {
                    "${it.toDateString(showYear)} (${it.toTimeString()})"
                })
                append(" - ")
                append(Date(end).let {
                    "${it.toDateString(showYear)} (${it.toTimeString()})"
                })
            }.toString())
        }
        else {
            Date(start).let {
                AndroidString(text = "${it.toDateString(showYear)} (${it.toTimeString()} - ${Date(end).toTimeString()})")
            }
        }
    }

    /**
     * Returns location string from EventLocation. If the location has been loaded before,
     * retrieve it from the cache, otherwise asynchronously call the respective API
     * to retrieve location data
     */
    private fun getEventLocation(location: EventLocation?): AndroidString? {
        location ?: return null
        return if (!TextUtils.isEmpty(location.name)) {
            AndroidString(text = location.name)
        }
        else if (location.placeId == null && location.googlePlaceId == null) {
            AndroidString(resId = R.string.event_card_location_latlng,
                    formatArgs = arrayOf(location.latitude?.toString() ?: "null", location.longitude?.toString() ?: "null"))
        }
        else if (!TextUtils.isEmpty(location.googlePlaceId)) {
            AndroidString(R.string.event_card_location_placeholder)
        }
        else null
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
        return if (userIds.any { it.equals(interactor.getCurrentUserId(), true) })  EventItemUiModel.AttendStatus.GOING
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

                // update the status icon
                val originalStatus = item.attendStatus
                when (newStatus) {
                    EventItemUiModel.AttendStatus.GOING -> {
                        statusCode = 2
                        animationItem = AnimationItem.JOIN_EVENT
                        message = AndroidString(R.string.event_card_join_success, arrayOf(item.title.text!!))
                        level = MessageLevel.SUCCESS
                    }
                    EventItemUiModel.AttendStatus.MAYBE -> {
                        statusCode = 1
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title.text!!))
                    }
                    EventItemUiModel.AttendStatus.DECLINED -> {
                        statusCode = 0
                        message = AndroidString(R.string.event_card_maybe_success, arrayOf(item.title.text!!))
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

                // show the animation
                animationItem?.let {
                    boardViewModel.observableAnimation.value = it
                }

                // update the status in the repos
                interactor.setItemAttendStatus(item.itemId, statusCode) {
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
                            AppLogger.e(it.error)
                            boardViewModel.observableViewAction.execute(arrayOf(
                                    Loading(false),
                                    Snackbar(AndroidString(io.jitrapon.glom.R.string.error_generic), level = MessageLevel.ERROR)
                            ))

                            // revert the status change
                            item.apply {
                                attendStatus = originalStatus
                            }
                            boardViewModel.observableBoard.value = boardViewModel.boardUiModel.apply {
                                requestPlaceInfoItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to
                                        arrayListOf(EventItemUiModel.ATTENDSTATUS, EventItemUiModel.ATTENDEES)) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun showEventPlan(boardViewModel: BoardViewModel, position: Int) {
        boardViewModel.observableNavigation.value = Navigation(NAVIGATE_TO_EVENT_PLAN, boardViewModel.getBoardItem(position) to false)
    }

    //endregion
    //region event detail item

    fun setPlaceProvider(placeProvider: PlaceProvider) {
        interactor.initWith(placeProvider)
    }

    /**
     * Initializes this ViewModel with the event item to display
     */
    fun setItem(item: BoardItem?, new: Boolean) {
        item.let {
            if (it == null) {
                AppLogger.w("Cannot set item because item is NULL")
            }
            else {
                if (item is EventItem) {
                    interactor.initWith(item = item)
                    item.itemInfo.let {
                        prevName = it.eventName
                        isNewItem = new
                        observableName.value = AndroidString(text = it.eventName) to false
                        observableStartDate.value = getEventDetailDate(it.startTime, true)
                        observableEndDate.value = getEventDetailDate(it.endTime, false)
                        observableLocation.value = getEventDetailLocation(it.location)
                        observableLocationDescription.value = getEventDetailLocationDescription(it.location)
                        loadPlaceInfoIfRequired(it.location?.name, it.location?.googlePlaceId)
                        observableLocationLatLng.value = getEventDetailLocationLatLng(it.location)
                        observableAttendeeTitle.value = getEventDetailAttendeeTitle(it.attendees)
                        observableAttendees.value = getEventDetailAttendees(it.attendees)
                        observableAttendStatus.value = getEventDetailAttendStatus(it.attendees)
                        observableNote.value = getEventDetailNote(it.note)
                        observablePlanStatus.value = getEventDetailPlanStatus(it.datePollStatus, it.placePollStatus)
                    }
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
     * Saves the current state and returns a model object with the state
     */
    fun saveItem(onSuccess: (Triple<BoardItem?, Boolean, Boolean>) -> Unit) {
        interactor.saveItem {
            when (it) {
                is AsyncSuccessResult -> onSuccess(Triple(it.result.first, it.result.second, isNewItem))
                is AsyncErrorResult -> handleError(it.error)
            }
        }
    }

    /**
     * Returns a formatted date in event detail
     */
    private fun getEventDetailDate(dateAsEpochMs: Long?, isStartDate: Boolean): AndroidString? {
        dateAsEpochMs ?: return AndroidString(resId = if (isStartDate) R.string.event_item_start_date_placeholder
                                                        else R.string.event_item_end_date_placeholder)
        return AndroidString(text = StringBuilder().apply {
            val date = Date(dateAsEpochMs)
            append(date.toDateString(true))
            append("\n")
            append(date.toTimeString())
        }.toString())
    }

    /**
     * Returns an event location in detail
     */
    private fun getEventDetailLocation(location: EventLocation?): AndroidString? {
        return getEventLocation(location)
    }

    /**
     * Returns an event location secondary text
     */
    private fun getEventDetailLocationDescription(location: EventLocation?): AndroidString? {
        location ?: return null
        return if (TextUtils.isEmpty(location.description)) null else AndroidString(text = location.description)
    }

    /**
     * Returns an event location latlng
     */
    private fun getEventDetailLocationLatLng(location: EventLocation?): LatLng? {
        location ?: return null
        return if (location.latitude != null && location.longitude != null) {
            LatLng(location.latitude, location.longitude)
        }
        else null
    }

    /**
     * Returns the title in the line separator on top of the event attendees
     */
    private fun getEventDetailAttendeeTitle(attendees: List<String>): AndroidString? {
        return AndroidString(resId = R.string.event_item_attendee_title, formatArgs = arrayOf(attendees.size.toString()))
    }

    /**
     * Returns UiModel for user avatars showing list of this event's attendees
     */
    private fun getEventDetailAttendees(attendees: List<String>): List<UserUiModel> {
        return interactor.getUsers(attendees).let {
            if (it.isNullOrEmpty()) ArrayList()
            else return ArrayList<UserUiModel>().apply {
                it!!.forEach {
                    it?.let { user ->
                        add(UserUiModel(user.avatar, user.userName))
                    }
                }
            }
        }
    }

    /**
     * Update the attend status for the current user
     */
    fun setEventDetailAttendStatus() {
        val buttonText: AndroidString
        val newStatus = when (attendStatus) {
            EventItemUiModel.AttendStatus.GOING -> {
                buttonText = AndroidString(R.string.event_item_join)
                EventItemUiModel.AttendStatus.MAYBE
            }
            EventItemUiModel.AttendStatus.MAYBE -> {
                buttonText = AndroidString(R.string.event_item_leave)
                EventItemUiModel.AttendStatus.GOING
            }
            EventItemUiModel.AttendStatus.DECLINED -> {
                buttonText = AndroidString(R.string.event_item_leave)
                EventItemUiModel.AttendStatus.GOING
            }
        }
        val statusCode = when (newStatus) {
            EventItemUiModel.AttendStatus.GOING -> 2
            EventItemUiModel.AttendStatus.MAYBE -> 1
            EventItemUiModel.AttendStatus.DECLINED -> 0
        }
        observableAttendStatus.value = observableAttendStatus.value!!.apply {
            text = null
            status = UiModel.Status.LOADING
        }
        interactor.setItemDetailAttendStatus(statusCode) {
            when (it) {
                is AsyncSuccessResult -> {
                    it.result?.let {
                        observableAttendees.value = getEventDetailAttendees(it)
                        observableAttendeeTitle.value = getEventDetailAttendeeTitle(it)
                    }
                    attendStatus = newStatus
                    observableAttendStatus.value = observableAttendStatus.value!!.apply {
                        text = buttonText
                        status = UiModel.Status.SUCCESS
                    }
                }
                is AsyncErrorResult -> {
                    observableAttendStatus.value = observableAttendStatus.value!!.apply {
                        text = null
                        status = UiModel.Status.SUCCESS
                    }
                    handleError(it.error)
                }
            }
        }
    }

    private fun getEventDetailAttendStatus(attendees: List<String>): ButtonUiModel {
        attendStatus = getEventAttendStatus(attendees)
        return when (attendStatus) {
            EventItemUiModel.AttendStatus.GOING -> ButtonUiModel(AndroidString(R.string.event_item_leave))
            EventItemUiModel.AttendStatus.MAYBE -> ButtonUiModel(AndroidString(R.string.event_item_join))
            EventItemUiModel.AttendStatus.DECLINED -> ButtonUiModel(AndroidString(R.string.event_item_join))
        }
    }

    fun updateEventDetailAttendStatus() {
        interactor.event.itemInfo.let {
            observableAttendeeTitle.value = getEventDetailAttendeeTitle(it.attendees)
            observableAttendees.value = getEventDetailAttendees(it.attendees)
            observableAttendStatus.value = getEventDetailAttendStatus(it.attendees)
        }
    }

    private fun getEventDetailNote(note: String?): AndroidString? {
        note ?: return null
        return AndroidString(text = note)
    }

    fun onNoteTextChanged(s: CharSequence) {
        // may need to convert from markdown text to String
        interactor.setItemNote(s.toString())
    }

    private fun getEventDetailPlanStatus(datePollStatus: Boolean, placePollStatus: Boolean): ButtonUiModel {
        return if (datePollStatus || placePollStatus) ButtonUiModel(AndroidString(R.string.event_item_view_plan))
        else ButtonUiModel(AndroidString(R.string.event_item_plan))
    }

    fun showEventDetailPlan(name: String) {
        interactor.event.let {
            it.itemInfo.eventName = name       // update the name to be the currently displayed one
            observableNavigation.value = Navigation(NAVIGATE_TO_EVENT_PLAN, it to false)
        }
    }

    //region autocomplete

    /**
     * Converts the suggestion as a text to be displayed in the drop-down
     */
    fun getSuggestionText(suggestion: Suggestion): AndroidString {
        return if (!TextUtils.isEmpty(suggestion.displayText)) AndroidString(text = suggestion.displayText) else 
            suggestion.selectData.let {
            when (it) {
                is Triple<*,*,*> -> {
                    if (it.first == Calendar.DAY_OF_MONTH) {
                        AndroidString(text = (it.third as Date).toRelativeDayString())
                    }
                    else {
                        AndroidString(text = (it.third as Date).toTimeString())
                    }
                }
                is PlaceInfo -> AndroidString(text = it.name)
                else -> AndroidString(text = it.toString())
            }
        }
    }

    /**
     * Synchronously filter place suggestions to display based on the specified query
     */
    fun filterLocationSuggestions(text: CharSequence): List<Suggestion> {
        return interactor.filterLocationSuggestions(text.toString())
    }

    /**
     * Synchronously filter suggestions to display based on the specified query
     */
    fun filterSuggestions(text: CharSequence): List<Suggestion> {
        return interactor.filterSuggestions(text.toString())
    }

    /**
     * Apply the current suggestion
     */
    fun selectSuggestion(currentText: Editable, suggestion: Suggestion) {
        val displayText = suggestion.selectData as? String ?: getSuggestionText(suggestion).text.toString()
        val delimiter = " "
        interactor.applySuggestion(currentText.toString(), suggestion, displayText, delimiter)

        val text = currentText.trim()
        val builder = SpannableStringBuilder(text)
        when (suggestion.selectData) {
            is String -> {
                if (suggestion.isConjunction) builder.append(delimiter).append(displayText)
                else builder.apply {
                    clear()
                    append(displayText)
                }
            }
            is Triple<*,*,*> -> {
                builder.append(delimiter).append(buildSpannedString {
                    bold { append(displayText) }
                })
                if (suggestion.selectData.second == true) {
                    observableStartDate.value = getEventDetailDate(interactor.getSelectedDate()?.time,
                            suggestion.selectData.second as Boolean)
                }
            }
            is PlaceInfo -> {
                val index = interactor.getSubQueryStartIndex()
                if (index >= 0 && index < currentText.trim().length) {
                    builder.replace(index, text.length, "")
                }
                builder.append(delimiter)
                builder.append(buildSpannedString {
                    bold { append(displayText) }
                })
                selectPlace(suggestion)
            }
        }
        observableName.value = AndroidString(text = SpannedString(builder)) to true
    }

    /**
     * Apply the current place suggestion
     */
    fun selectPlace(suggestion: Suggestion) {
        suggestion.selectData.let {
            if (it is PlaceInfo) {
                EventLocation(it.latitude, it.longitude, it.googlePlaceId, it.placeId, it.name, it.description).apply {
                    interactor.setItemLocation(this)
                    observableLocation.value = getEventDetailLocation(this)
                    observableLocationDescription.value = getEventDetailLocationDescription(this)
                    observableLocationLatLng.value = getEventDetailLocationLatLng(this)
                    loadPlaceInfoIfRequired(it.name, it.googlePlaceId)
                }
            }
        }
    }

    private fun loadPlaceInfoIfRequired(customName: String?, googlePlaceId: String?) {
        if (!TextUtils.isEmpty(googlePlaceId)) {
            interactor.loadPlaceInfo(customName) { result ->
                when (result) {
                    is AsyncSuccessResult -> {
                        result.result.let {
                            observableLocation.value = getEventDetailLocation(it)
                            observableLocationDescription.value = getEventDetailLocationDescription(it)
                            observableLocationLatLng.value = getEventDetailLocationLatLng(it)
                        }
                    }
                    is AsyncErrorResult -> handleError(result.error)
                }
            }
        }
    }

    /**
     * Remove the suggestion
     */
    fun removeSuggestion(suggestion: Suggestion) {
        interactor.removeSuggestion(suggestion)
    }

    /**
     * Validates event name
     */
    fun validateName(input: String) {
        if (!InputValidator.validateNotEmpty(input))
            observableName.value = AndroidString(resId = R.string.event_item_name_error, status = UiModel.Status.ERROR) to false
        else
            observableName.value = AndroidString(status = UiModel.Status.EMPTY) to false
    }

    /**
     * Displays the datetime picker
     */
    fun showDateTimePicker(isStartDate: Boolean) {
        val startDate = interactor.getItemDate(true)
        val endDate = interactor.getItemDate(false)
        val defaultDate: Date =
        if (isStartDate) {
            startDate ?: Date().roundToNextHalfHour()
        }
        else {
            endDate ?: startDate?.addHour(1) ?: Date().roundToNextHalfHour().addHour(1)
        }
        observableDateTimePicker.value = DateTimePickerUiModel(defaultDate, if (!isStartDate && startDate != null) startDate else null) to isStartDate
    }

    /**
     * Updates the date of the event
     */
    fun setDate(date: Date?, isStartDate: Boolean) {
        interactor.let {
            it.setItemDate(date, isStartDate)

            observableStartDate.value = getEventDetailDate(it.getItemDate(true)?.time, true)
            observableEndDate.value = getEventDetailDate(it.getItemDate(false)?.time, false)
            observableDateTimePicker.value = null
        }
    }

    fun cancelSetDate() {
        observableDateTimePicker.value = null
    }

    /**
     * Navigates to a third-party map application
     */
    fun navigateToMap() {
        observableViewAction.value = interactor.getItemLocation()?.let {
            val latLng = if (it.latitude != null && it.longitude != null) {
                LatLng(it.latitude, it.longitude)
            } else null
            Navigation(Const.NAVIGATE_TO_MAP_SEARCH, Triple(latLng, it.name, it.googlePlaceId))
        }
    }

    /**
     * Update the location text
     */
    fun onLocationTextChanged(charSequence: CharSequence) {
        interactor.setItemLocation(charSequence)
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

    fun getObservableName(): LiveData<Pair<AndroidString, Boolean>> = observableName

    fun getObservableStartDate(): LiveData<AndroidString> = observableStartDate

    fun getObservableEndDate(): LiveData<AndroidString> = observableEndDate

    fun getObservableDateTimePicker(): LiveData<Pair<DateTimePickerUiModel, Boolean>> = observableDateTimePicker

    fun getObservableLocation(): LiveData<AndroidString> = observableLocation

    fun getObservableLocationDescription(): LiveData<AndroidString> = observableLocationDescription

    fun getObservableLocationLatLng(): LiveData<LatLng> = observableLocationLatLng

    fun getObservableAttendeeTitle(): LiveData<AndroidString> = observableAttendeeTitle

    fun getObservableAttendees(): LiveData<List<UserUiModel>> = observableAttendees

    fun getObservableAttendStatus(): LiveData<ButtonUiModel> = observableAttendStatus

    fun getObservableNote(): LiveData<AndroidString?> = observableNote

    fun getObservablePlanStatus(): LiveData<ButtonUiModel> = observablePlanStatus

    fun getObservableNavigation(): LiveData<Navigation> = observableNavigation

    override fun cleanUp() {
        //nothing yet
    }

    //endregion
}