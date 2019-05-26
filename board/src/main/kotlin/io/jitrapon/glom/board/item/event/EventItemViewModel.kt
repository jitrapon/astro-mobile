package io.jitrapon.glom.board.item.event

import android.location.Address
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.TextUtils
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.base.viewmodel.runAsync
import io.jitrapon.glom.board.*
import io.jitrapon.glom.board.Const.NAVIGATE_TO_EVENT_PLAN
import io.jitrapon.glom.board.Const.NAVIGATE_TO_PLACE_PICKER
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.BoardItemUiModel
import io.jitrapon.glom.board.item.BoardItemViewModel
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.plan.PLAN_EVENT_DATE_PAGE
import io.jitrapon.glom.board.item.event.plan.PLAN_EVENT_PLACE_PAGE
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
    private val observableDateTimePicker = LiveEvent<Pair<DateTimePickerUiModel, Boolean>>()

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

    /* cached attend status */
    private var attendStatus: EventItemUiModel.AttendStatus = EventItemUiModel.AttendStatus.MAYBE

    /* observable event note */
    private val observableNote = MutableLiveData<AndroidString?>()

    /* observable source of this event */
    private val observableSource = MutableLiveData<EventSourceUiModel>()

    /* observable action buttons for date time section */
    private val observableDateTimeActions = MutableLiveData<ArrayList<ButtonUiModel>>()

    /* observable action buttons for location section */
    private val observableLocationActions = MutableLiveData<ArrayList<ButtonUiModel>>()

    /* observable action buttons for attendees section */
    private val observableAttendeesActions = MutableLiveData<ArrayList<ButtonUiModel>>()

    init {
        BoardInjector.getComponent().inject(this)
    }

    //region event board item

    override fun toUiModel(item: BoardItem, syncStatus: SyncStatus): BoardItemUiModel {
        return (item as EventItem).let {
            EventItemUiModel(
                    itemId = it.itemId,
                    title = AndroidString(text = it.itemInfo.eventName),
                    dateTime = getEventDate(it.itemInfo.startTime, it.itemInfo.endTime, it.itemInfo.isFullDay),
                    location = getEventLocation(it.itemInfo.location),
                    mapLatLng = getEventLatLng(it.itemInfo.location),
                    attendeesAvatars = getEventAttendees(it.itemInfo.attendees),
                    attendStatus = getEventAttendStatus(it.itemInfo.attendees),
                    status = getSyncStatus(syncStatus),
                    isPlanning = it.itemInfo.datePollStatus || it.itemInfo.placePollStatus,
                    sourceIcon = getEventSourceIcon(it.itemInfo.source),
                    sourceDescription = getEventSourceDescription(it.itemInfo.source)
            )
        }
    }

    /**
     * Returns an AndroidImage from an EventSource
     */
    private fun getEventSourceIcon(source: EventSource): AndroidImage? {
        return when {
            !source.sourceIconUrl.isNullOrEmpty() -> AndroidImage(imageUrl = source.sourceIconUrl)
            source.calendar?.color != null -> AndroidImage(colorInt = source.calendar.color)
            else -> null
        }
    }

    /**
     * Returns an AndroidString from an EventSource
     */
    private fun getEventSourceDescription(source: EventSource): AndroidString? {
        return when {
            source.calendar != null -> getLocalCalendarSourceName(source.calendar.displayName)
            !source.description.isNullOrEmpty() -> AndroidString(text = source.description)
            else -> null
        }
    }

    private fun getLocalCalendarSourceName(displayName: String?): AndroidString? {
        return when {
            displayName.isNullOrEmpty() -> null
            displayName.contains("@") -> AndroidString(R.string.event_card_calendar_source)
            else -> AndroidString(text = displayName)
        }
    }

    /**
     * Returns a formatted date range
     */
    private fun getEventDate(start: Long?, end: Long?, isFullDay: Boolean): AndroidString? {
        start ?: return null
        val endMs = if (isFullDay && end != null) {
            Date(end).addDay(-1).time
        } else end
        val startDate = Calendar.getInstance().apply { time = Date(start) }
        val currentDate = Calendar.getInstance()
        var showYear = startDate[Calendar.YEAR] != currentDate[Calendar.YEAR]

        // if end datetime is not present, only show start time
        if (endMs == null || endMs == start) return Date(start).let {
            val time = if (isFullDay) "" else " (${it.toTimeString()})"
            AndroidString(text = "${it.toDateString(showYear)}$time")
        }

        // if end datetime is present
        // show one date with time range if end datetime is within the same day
        // (i.e. 20 Jun, 2017 (10:30 AM - 3:00 PM), otherwise
        // show 20 Jun, 2017 (10:30 AM) - 21 Jun, 2017 (10:30 AM)
        val endDate = Calendar.getInstance().apply { time = Date(endMs) }
        val startYearNotEqEndYear = startDate[Calendar.YEAR] != endDate[Calendar.YEAR]
        return if (startYearNotEqEndYear || startDate[Calendar.DAY_OF_YEAR] != endDate[Calendar.DAY_OF_YEAR]) {
            showYear = showYear || startYearNotEqEndYear
            AndroidString(text = StringBuilder().apply {
                append(Date(start).let {
                    val time = if (isFullDay) "" else " (${it.toTimeString()})"
                    "${it.toDateString(showYear)}$time"
                })
                append(" - ")
                append(Date(endMs).let {
                    val time = if (isFullDay) "" else " (${it.toTimeString()})"
                    "${it.toDateString(showYear)}$time"
                })
            }.toString())
        }
        else {
            Date(start).let {
                val time = if (isFullDay) "" else " (${it.toTimeString()} - ${Date(endMs).toTimeString()})"
                AndroidString(text = "${it.toDateString(showYear)}$time")
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
        else if (location.placeId == null && location.googlePlaceId == null && location.latitude != null && location.longitude != null) {
            AndroidString(resId = R.string.event_card_location_latlng,
                    formatArgs = arrayOf(location.latitude.toString(), location.longitude.toString()))
        }
        else if (!TextUtils.isEmpty(location.googlePlaceId)) {
            AndroidString(R.string.event_card_location_placeholder)
        }
        else null
    }

    fun updateEventLocationFromPlace(itemId: String, place: Place?) {
        interactor.updateItemPlace(itemId, place?.name, place?.address, place?.latLng, place?.id, false) {
            if (it is AsyncErrorResult) {
                AppLogger.e(it.error)
            }
        }
    }

    fun updateEventLocationFromAddress(itemId: String, name: String?, address: Address?) {
        interactor.updateItemPlace(itemId, name, address?.fullAddress, address?.latLng, null, false) {
            if (it is AsyncErrorResult) {
                AppLogger.e(it.error)
            }
        }
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
                    requestAddressItemIds = null
                    diffResult = null
                    itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to arrayListOf(ATTENDSTATUS)) }
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
                                requestAddressItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to
                                        arrayListOf(ATTENDSTATUS, ATTENDEES)) }
                            }
                            boardViewModel.observableViewAction.value = Snackbar(message, level = level)
                        }
                        is AsyncErrorResult -> {
                            handleError(it.error, observable = boardViewModel.observableViewAction)

                            // revert the status change
                            item.apply {
                                attendStatus = originalStatus
                            }
                            boardViewModel.observableBoard.value = boardViewModel.boardUiModel.apply {
                                requestPlaceInfoItemIds = null
                                requestAddressItemIds = null
                                diffResult = null
                                itemsChangedIndices = ArrayList<Pair<Int, Any?>>().apply { add(position to
                                        arrayListOf(ATTENDSTATUS, ATTENDEES)) }
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
        runAsync({
            if (item == null || item !is EventItem) throw Exception("Item is either NULL or not a")
            item.run {
                isItemEditable = isEditable
                val editableStatus = getEditableStatus(isEditable)
                interactor.initWith(item = this)
                itemInfo.let {
                    prevName = it.eventName
                    isNewItem = new
                    attendStatus = getEventAttendStatus(it.attendees)
                    EventItemDetailUiModel(
                        AndroidString(text = it.eventName, status = editableStatus) to false,
                        getEventDetailDate(it.startTime, true, it.source, it.isFullDay)?.apply { if (!isItemEditable) status = editableStatus },
                        getEventDetailDate(it.endTime, false, it.source, it.isFullDay)?.apply { if (!isItemEditable) status = editableStatus },
                        it.location,
                        getEventDetailLocation(it.location)?.apply { status = editableStatus },
                        getEventDetailLocationDescription(it.location)?.apply { status = editableStatus },
                        getEventDetailLocationLatLng(it.location),
                        getEventDetailAttendeeTitle(it.attendees),
                        getEventDetailAttendees(it.attendees),
                        getEventDetailNote(it.note)?.apply { status = editableStatus },
                        getEventDetailSource(it.source),
                        getEventDetailDateTimeActions(it.datePollStatus),
                        getEventDetailLocationActions(it.location, it.placePollStatus),
                        getEventDetailAttendeesActions(UiModel.Status.SUCCESS)
                    )
                }
            }
        }, {
            observableName.value = it.name
            observableStartDate.value = it.startDate
            observableEndDate.value = it.endDate
            observableLocation.value = it.locationName
            observableLocationDescription.value = it.locationDescription
            it.location?.let { loadPlaceAsync(it.name, it.googlePlaceId, it.latitude, it.longitude) }
            observableLocationLatLng.value = it.locationLatLng
            observableAttendeeTitle.value = it.attendeeTitle
            observableAttendees.value = it.attendees
            observableNote.value = it.note
            observableSource.value = it.source
            observableDateTimeActions.value = it.dateTimeActionButtons
            observableAttendeesActions.value = it.attendeesActionButtons
            observableLocationActions.value = it.locationActionButtons
        }, {
            handleError(it)
        })
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
    private fun getEventDetailDate(dateAsEpochMs: Long?, isStartDate: Boolean, source: EventSource, isFullDay: Boolean): AndroidString? {
        dateAsEpochMs ?: return AndroidString(resId = if (isStartDate) R.string.event_item_start_date_placeholder
                                                        else R.string.event_item_end_date_placeholder, status = UiModel.Status.EMPTY)
        return AndroidString(text = StringBuilder().apply {
            val date = Date(dateAsEpochMs).run {
                if (!isStartDate && isFullDay) {
                    addDay(-1)
                }
                else this
            }
            append(date.toDateString(true))
            if (!isFullDay) {
                append("   ")
                append(date.toTimeString())
            }
        }.toString(), status = if (isStartDate && source.calendar != null) UiModel.Status.EMPTY else UiModel.Status.SUCCESS)
    }

    /**
     * Returns an event location in detail
     */
    private fun getEventDetailLocation(location: EventLocation?): AndroidString? {
        return getEventLocation(location) ?: if (isItemEditable) null else AndroidString(R.string.event_item_no_location_placeholder)
    }

    /**
     * Returns an event location secondary text
     */
    private fun getEventDetailLocationDescription(location: EventLocation?): AndroidString? {
        location ?: return null
        return if (TextUtils.isEmpty(location.address)) null else AndroidString(text = location.address)
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
    private fun setEventDetailAttendStatus() {
        val nextStatus = when (attendStatus) {
            EventItemUiModel.AttendStatus.GOING -> EventItemUiModel.AttendStatus.MAYBE
            EventItemUiModel.AttendStatus.MAYBE -> EventItemUiModel.AttendStatus.GOING
            EventItemUiModel.AttendStatus.DECLINED -> EventItemUiModel.AttendStatus.GOING
        }

        observableAttendeesActions.value = getEventDetailAttendeesActions(UiModel.Status.LOADING)

        interactor.setItemDetailAttendStatus(nextStatus) {
            when (it) {
                is AsyncSuccessResult -> {
                    it.result?.let { userIds ->
                        attendStatus = getEventAttendStatus(userIds)
                        observableAttendeesActions.value = getEventDetailAttendeesActions(UiModel.Status.SUCCESS)
                        observableAttendees.value = getEventDetailAttendees(userIds)
                        observableAttendeeTitle.value = getEventDetailAttendeeTitle(userIds)
                    }
                }
                is AsyncErrorResult -> {
                    handleError(it.error)

                    observableAttendeesActions.value = getEventDetailAttendeesActions(UiModel.Status.SUCCESS)
                }
            }
        }
    }

    private fun getEventDetailAttendStatus(joinActionItem: ActionItem,
                                           leaveActionItem: ActionItem,
                                           status: UiModel.Status): ButtonUiModel {
        return when (attendStatus) {
            EventItemUiModel.AttendStatus.GOING -> ButtonUiModel(
                AndroidString(leaveActionItem.title),
                AndroidImage(leaveActionItem.drawable),
                leaveActionItem,
                status)
            EventItemUiModel.AttendStatus.MAYBE -> ButtonUiModel(
                AndroidString(joinActionItem.title),
                AndroidImage(joinActionItem.drawable),
                joinActionItem,
                status)
            EventItemUiModel.AttendStatus.DECLINED -> ButtonUiModel(
                AndroidString(joinActionItem.title),
                AndroidImage(joinActionItem.drawable),
                joinActionItem,
                status)
        }
    }

    fun updateEventFromPlan() {
        interactor.event.itemInfo.let {
            if (interactor.isItemModified()) {
                observableAttendeeTitle.value = getEventDetailAttendeeTitle(it.attendees)
                observableAttendees.value = getEventDetailAttendees(it.attendees)
                attendStatus = getEventAttendStatus(it.attendees)
                observableAttendeesActions.value = getEventDetailAttendeesActions(UiModel.Status.SUCCESS)

                observableStartDate.value = getEventDetailDate(interactor.getItemDate(true)?.time, true, it.source, it.isFullDay)
                observableEndDate.value = getEventDetailDate(interactor.getItemDate(false)?.time, false, it.source, it.isFullDay)
                observableDateTimePicker.value = null

                it.location?.apply {
                    observableLocation.value = getEventDetailLocation(this)
                    observableLocationDescription.value = getEventDetailLocationDescription(this)
                    observableLocationLatLng.value = getEventDetailLocationLatLng(this)
                }
            }
        }
    }

    private fun getEventDetailNote(note: String?): AndroidString? {
        note ?: return null
        return AndroidString(text = note)
    }

    fun onNoteTextChanged(s: CharSequence) {
        // may need to convert from markdown text to String
        interactor.setItemNote(s)
    }

    private fun showEventDetailPlan(name: String, page: Int) {
        interactor.event.let {
            it.itemInfo.eventName = name
            observableNavigation.value = Navigation(NAVIGATE_TO_EVENT_PLAN, Triple(it, false, page))
        }
    }

    private fun getEventDetailSource(source: EventSource): EventSourceUiModel {
        return EventSourceUiModel(
                when {
                    !source.sourceIconUrl.isNullOrEmpty() -> AndroidImage(imageUrl = source.sourceIconUrl)
                    source.calendar?.color != null -> AndroidImage(resId = io.jitrapon.glom.R.drawable.bg_solid_circle_18dp, tint = source.calendar.color)
                    else -> AndroidImage(resId = io.jitrapon.glom.R.drawable.ic_calendar_multiple, tint = null)
                },
                when {
                    source.calendar != null -> AndroidString(text = source.calendar.displayName)
                    !source.description.isNullOrEmpty() -> AndroidString(text = source.description)
                    else -> AndroidString(resId = R.string.event_item_source_none)
                },
                if (!isItemEditable) UiModel.Status.NEGATIVE else UiModel.Status.SUCCESS
        )
    }

    fun showEventDetailSources() {
        interactor.getSyncedAndWritableSources { result ->
            when (result) {
                is AsyncSuccessResult -> {
                    runAsync({
                        // add the first choice, which is no calendars and other external sources
                        // events will be exclusive our app
                        val choices = ArrayList<PreferenceItemUiModel>().apply {
                            add(PreferenceItemUiModel(null, AndroidString(resId = R.string.event_item_source_none)))
                        }

                        // after that we add all other writable sources
                        choices.addAll(result.result.map { source ->
                            PreferenceItemUiModel(
                                when {
                                    !source.sourceIconUrl.isNullOrEmpty() -> AndroidImage(imageUrl = source.sourceIconUrl)
                                    source.calendar?.color != null -> AndroidImage(resId = io.jitrapon.glom.R.drawable.ic_checkbox_blank_circle, tint = source.calendar.color)
                                    else -> null
                                },
                                when {
                                    source.calendar != null -> AndroidString(text = source.calendar.displayName)
                                    !source.description.isNullOrEmpty() -> AndroidString(text = source.description)
                                    else -> AndroidString(text = null)
                                }
                            )
                        })
                         choices
                    }, {
                        observableViewAction.value = PresentChoices(AndroidString(R.string.event_item_select_sources), it) { position ->
                            observableSource.value = interactor.setItemSource(
                                if (position == 0) EventSource(null, null, null, interactor.circleId)
                                else result.result[position - 1]
                            ).let { source ->
                                getEventDetailSource(source)
                            }
                        }
                    }, {
                        handleError(it)
                    })
                }
            }
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
        return interactor.setItemName(text.toString(), false)
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
                    val info = interactor.event.itemInfo
                    observableStartDate.value = getEventDetailDate(interactor.getSelectedDate()?.time,
                            suggestion.selectData.second as Boolean, info.source, info.isFullDay)
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
                EventLocation(it.latitude, it.longitude, it.googlePlaceId, it.placeId, it.name, it.description, it.address).apply {
                    interactor.setItemLocation(this)
                    observableLocation.value = getEventDetailLocation(this)
                    observableLocationDescription.value = getEventDetailLocationDescription(this)
                    observableLocationLatLng.value = getEventDetailLocationLatLng(this)
                    loadPlaceAsync(it.name, it.googlePlaceId, it.latitude, it.longitude)
                }
            }
        }
    }

    fun selectPlace(place: Place?) {
        place?.let {
            EventLocation(it.latLng?.latitude, it.latLng?.longitude, it.id, null, it.name.toString(), null, it.address.toString()).apply {
                interactor.setItemLocation(this)
                observableLocation.value = getEventDetailLocation(this)
                observableLocationDescription.value = getEventDetailLocationDescription(this)
                observableLocationLatLng.value = getEventDetailLocationLatLng(this)
            }
        }
    }

    private fun loadPlaceAsync(customName: String?, googlePlaceId: String?, latitude: Double?, longitude: Double?) {
        if (!TextUtils.isEmpty(googlePlaceId) && latitude == null && longitude == null) {
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
        if (!isItemEditable) return

        val startDate = interactor.getItemDate(true)
        val endDate = interactor.getItemDate(false)
        val defaultDate: Date =
        if (isStartDate) {
            startDate ?: Date().roundToNextHalfHour()
        }
        else {
            endDate ?: startDate?.addHour(1) ?: Date().roundToNextHalfHour().addHour(1)
        }
        observableDateTimePicker.value = DateTimePickerUiModel(
            if (isStartDate || !interactor.event.itemInfo.isFullDay) defaultDate else defaultDate.addDay(-1),
            if (!isStartDate && startDate != null) startDate else null,
            interactor.event.itemInfo.isFullDay) to isStartDate
    }

    /**
     * Updates the date of the event
     */
    fun setDate(date: Date?, isStartDate: Boolean, isFullDay: Boolean? = null) {
        interactor.let {
            val fullDay = isFullDay ?: it.event.itemInfo.isFullDay
            it.setItemDate(date, isStartDate, fullDay)

            val info = it.event.itemInfo
            observableStartDate.value = getEventDetailDate(it.getItemDate(true)?.time, true, info.source, fullDay)
            observableEndDate.value = getEventDetailDate(it.getItemDate(false)?.time, false, info.source, fullDay)
            observableDateTimePicker.value = null
        }
    }

    private fun navigateToPlacePicker() {
        observableViewAction.value = Navigation(NAVIGATE_TO_PLACE_PICKER)
    }

    /**
     * Navigates to a third-party map application
     */
    fun navigateToMap(withDirection: Boolean) {
        observableViewAction.value = interactor.getItemLocation()?.let {
            val latLng = if (it.latitude != null && it.longitude != null) {
                LatLng(it.latitude, it.longitude)
            } else null
            Navigation(Const.NAVIGATE_TO_MAP_SEARCH, NavigationArguments(latLng, it.name, it.googlePlaceId, withDirection))
        }
    }

    /**
     * Update the location text
     */
    fun onLocationTextChanged(charSequence: CharSequence) {
        if (charSequence.isEmpty()) {
            interactor.setItemLocation(null)

            observableLocationActions.value = getEventDetailLocationActions(interactor.event.itemInfo.location,
                    interactor.event.itemInfo.placePollStatus)
        }
        else {
            val prevLocation = interactor.event.itemInfo.location
            interactor.setItemLocation(charSequence)

            if (prevLocation == null && interactor.event.itemInfo.location != null) {
                observableLocationActions.value = getEventDetailLocationActions(interactor.event.itemInfo.location,
                        interactor.event.itemInfo.placePollStatus)
            }
        }
    }

    /**
     * Navigates to the Place Picker widget
     */
    private fun showPlacePicker() {
        observableNavigation.value = Navigation(NAVIGATE_TO_PLACE_PICKER, null)
    }

    //endregion
    //region view states

    fun closeItem() {
        interactor.clearSession()
    }

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = false

    /**
     * Returns the event name before change
     */
    fun getPreviousName(): String? = prevName

    //endregion
    //region action buttons

    fun handleActionItem(uiModel: Any?, vararg arguments: Any?) {
        (uiModel as? ActionItem)?.let {
            when (it) {
                ActionItem.PLAN_DATETIME -> {
                    (arguments.getOrNull(0) as? String)?.let { name ->
                        showEventDetailPlan(name, PLAN_EVENT_DATE_PAGE)
                    }
                }
                ActionItem.PLAN_LOCATION -> {
                    (arguments.getOrNull(0) as? String)?.let { name ->
                        showEventDetailPlan(name, PLAN_EVENT_PLACE_PAGE)
                    }
                }
                ActionItem.PICK_PLACE -> {
                    navigateToPlacePicker()
                }
                ActionItem.MAP -> {
                    navigateToMap(false)
                }
                ActionItem.DIRECTION -> {
                    navigateToMap(true)
                }
                ActionItem.CALL -> {

                }
                ActionItem.JOIN, ActionItem.LEAVE -> {
                    setEventDetailAttendStatus()
                }
                else -> {}
            }
        }
    }

    private fun ActionItem.toUiModel(status: UiModel.Status = UiModel.Status.SUCCESS): ButtonUiModel =
            ButtonUiModel(AndroidString(title), AndroidImage(drawable), this, status)

    private fun getEventDetailDateTimeActions(status: Boolean): ArrayList<ButtonUiModel> {
        return ArrayList<ButtonUiModel>().apply {
            add(ActionItem.PLAN_DATETIME.toUiModel(if (status) UiModel.Status.POSITIVE else UiModel.Status.SUCCESS))
        }
    }

    private fun getEventDetailLocationActions(location: EventLocation?, status: Boolean): ArrayList<ButtonUiModel> {
        return ArrayList<ButtonUiModel>().apply {
            val hasLocationStatus = if (!location?.name.isNullOrEmpty() ||
                    (location?.latitude != null && location.longitude != null)) UiModel.Status.POSITIVE else UiModel.Status.SUCCESS
            add(ActionItem.PLAN_LOCATION.toUiModel(if (status) UiModel.Status.POSITIVE else UiModel.Status.SUCCESS))
            add(ActionItem.PICK_PLACE.toUiModel())
            add(ActionItem.MAP.toUiModel(hasLocationStatus))
            add(ActionItem.DIRECTION.toUiModel(hasLocationStatus))
        }
    }

    private fun getEventDetailAttendeesActions(status: UiModel.Status): ArrayList<ButtonUiModel> {
        return ArrayList<ButtonUiModel>().apply {
            add(getEventDetailAttendStatus(ActionItem.JOIN, ActionItem.LEAVE, status))
        }
    }

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

    fun getObservableNote(): LiveData<AndroidString?> = observableNote

    fun getObservableSource(): LiveData<EventSourceUiModel> = observableSource

    fun getObservableDateTimeActions(): LiveData<ArrayList<ButtonUiModel>> = observableDateTimeActions

    fun getObservableLocationActions(): LiveData<ArrayList<ButtonUiModel>> = observableLocationActions

    fun getObservableAttendeesActions(): LiveData<ArrayList<ButtonUiModel>> = observableAttendeesActions

    override fun cleanUp() {
        interactor.cleanup()
    }

    //endregion
}
