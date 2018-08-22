package io.jitrapon.glom.board.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.text.TextUtils
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.isNullOrEmpty
import io.jitrapon.glom.base.util.toDateString
import io.jitrapon.glom.base.util.toTimeString
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.board.BoardInjector
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.Const
import io.jitrapon.glom.board.R
import java.util.*
import javax.inject.Inject

/**
 * Manages view states for plan event screens
 */
class PlanEventViewModel : BaseViewModel() {

    @Inject
    lateinit var interactor: EventItemInteractor

    /* observable background URL */
    private val observableBackground = MutableLiveData<String?>()

    /* observable event name */
    private val observableName = MutableLiveData<AndroidString>()

    /* observable attendee list */
    private val observableAttendees = MutableLiveData<List<UserUiModel>>()

    /* observable attendee list label */
    private val observableAttendeesLabel = MutableLiveData<AndroidString>()

    /* observable join button */
    private val observableJoinButton = MutableLiveData<ButtonUiModel>()

    /* observable date time picker */
    private val observableDateTimePicker = LiveEvent<DateTimePickerUiModel>()

    /* whether or not user is attending */
    private var isUserAttending: Boolean = false

    /* observable navigation event */
    private val observableNavigation = LiveEvent<Navigation>()

    /* cached event date plan UI model for reuse */
    private val datePlan: EventDatePlanUiModel

    /* observable date polls */
    private val observableDatePlan = MutableLiveData<EventDatePlanUiModel>()

    /* whether or not the current user ID owns this item */
    private var isUserAnOwner: Boolean = false

    /* whether or not date plan has been loaded */
    private var isDatePlanLoaded: Boolean = false

    /* observable date poll status (opened/closed) button */
    private val observableDateVoteStatusButton = MutableLiveData<ButtonUiModel>()

    /* observable select date button for owners */
    private val observableDateSelectButton = MutableLiveData<ButtonUiModel>()

    /* last selected date poll position */
    private var lastSelectedDatePollIndex: Int? = null

    /* cached event place plan UI model for reuse */
    private val placePlan: EventPlacePlanUiModel

    /* place polls */
    private val observablePlacePlan = MutableLiveData<EventPlacePlanUiModel>()

    /* whether or not place plan has been loaded */
    private var isPlacePlanLoaded: Boolean = false

    init {
        BoardInjector.getComponent().inject(this)
        datePlan = EventDatePlanUiModel(ArrayList<EventDatePollUiModel>(), null, UiModel.Status.EMPTY)
        placePlan = EventPlacePlanUiModel(ArrayList<EventPlacePollUiModel>(), null, UiModel.Status.EMPTY)
    }

    /**
     * Initializes this ViewModel with the event item to display.
     * Must be called before any other functions in this ViewModel
     *
     * Pass in null for item if this item has not been created before
     */
    fun setItem(placeProvider: PlaceProvider?, item: BoardItem?) {
        item.let {
            if (it == null) {
                handleError(Exception("Item is NULL but expected not NULL"))
            }
            else {
                if (item is EventItem) {
                    interactor.initWith(placeProvider, item, true)

                    item.itemInfo.let {
                        isUserAnOwner = item.owners.contains(interactor.getCurrentUserId())

                        // set up the overview page
                        isUserAttending = it.attendees.contains(interactor.getCurrentUserId())
                        observableName.value = AndroidString(text = it.eventName)
                        observableAttendees.value = it.attendees.toUiModel()
                        observableAttendeesLabel.value = getAttendeesLabel(it.attendees)
                        observableJoinButton.value = getJoinButtonStatus()
                        observableBackground.value = "https://mir-s3-cdn-cf.behance.net/project_modules/1400/82cf1121015893.59b6dc9dc8015.jpg"

                        // set up the date plan page
                        observableDatePlan.value = datePlan.apply {
                            itemsChangedIndices = null
                        }

                        // refresh the bottom buttons
                        refreshDatePollStatusButton(observableDateVoteStatusButton.value ?: ButtonUiModel(null))
                        refreshDatePollSelectButton(observableDateSelectButton.value ?: ButtonUiModel(null))
                    }
                }
            }
        }
    }

    fun saveItem() {
        observableNavigation.value = Navigation(Const.NAVIGATE_BACK, if (interactor.isItemModified()) interactor.event else null)
    }

    fun getFirstVisiblePageIndex(): Int {
        return interactor.event.itemInfo.let {
            when {
                it.datePollStatus -> 1
                it.placePollStatus -> 2
                else -> 0
            }
        }
    }

    //region event overview

    /**
     * Returns UiModel for user avatars showing list of this event's attendees
     */
    private fun List<String>.toUiModel(): List<UserUiModel> {
        return interactor.getUsers(this).let {
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
     * Gets list of attendees Ui Models from the list of their usernames
     */
    private fun getAttendeesLabel(attendees: List<String>): AndroidString {
        return if (isUserAttending)
            AndroidString(R.string.event_plan_user_attending, arrayOf(attendees.size.toString()))
        else AndroidString(R.string.event_plan_user_not_attending, arrayOf(attendees.size.toString()))
    }

    /**
     * Gets the current join button based on the user's attend status
     */
    private fun getJoinButtonStatus(): ButtonUiModel {
        return if (isUserAttending)
            ButtonUiModel(AndroidString(R.string.event_item_leave), UiModel.Status.NEGATIVE)
        else ButtonUiModel(AndroidString(R.string.event_item_join), UiModel.Status.POSITIVE)
    }

    /**
     * Toggles the state of the attend status
     */
    fun toggleAttendStatus() {
        val newStatus = if (isUserAttending) 0 else 2
        observableViewAction.value = Loading(true)

        interactor.setItemDetailAttendStatus(newStatus) {
            observableViewAction.value = Loading(false)
            when (it) {
                is AsyncSuccessResult -> {
                    isUserAttending = newStatus == 2
                    it.result?.let {
                        observableAttendeesLabel.value = getAttendeesLabel(it)
                        observableAttendees.value = it.toUiModel()
                        observableJoinButton.value = getJoinButtonStatus()
                    }
                }
                is AsyncErrorResult -> handleError(it.error)
            }
        }
    }

    //endregion
    //region event plan date

    fun loadDatePolls() {
        if (isDatePlanLoaded) return

        observableDatePlan.value = datePlan.apply { status = UiModel.Status.LOADING }

        interactor.loadDatePlan { it ->
            isDatePlanLoaded = true

            when (it) {
                is AsyncSuccessResult -> {
                    if (!interactor.event.itemInfo.datePollStatus && isUserAnOwner) {
                        refreshAndSelectDatePoll()
                    }
                    else {
                        refreshDatePolls(it.result)
                    }
                }
                is AsyncErrorResult -> {
                    observableDatePlan.value = datePlan.apply { status = UiModel.Status.ERROR }

                    handleError(it.error)
                }
            }
        }
    }

    private fun refreshDatePolls(result: List<EventDatePoll>) {
        observableDatePlan.value = datePlan.apply {
            itemsChangedIndices = null
            status = UiModel.Status.SUCCESS
            datePolls.apply {
                clear()
                val event = interactor.event
                result.forEach { add(it.toUiModel(event)) }
            }
        }
    }

    private fun EventDatePoll.toUiModel(event: EventItem, status: UiModel.Status? = null): EventDatePollUiModel {
        // not owner: see own upvote, date poll status: true || false
        // owner: see own upvote only date poll status == true, false otherwise
        val (dateString, timeString) = getPollDateTime(startTime, endTime)
        return if (status == null) {
            val isDatePollOpened = event.itemInfo.datePollStatus
            val isOwner = event.owners.contains(interactor.getCurrentUserId())
            val isUpvoted = users.contains(interactor.getCurrentUserId())
            val showUpvoted = if (!isUpvoted) false else !isOwner || (isOwner && isDatePollOpened)
            EventDatePollUiModel(id, dateString, timeString, Date(startTime), endTime?.let { Date(it) }, users.size,
                    if (showUpvoted) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE)
        }
        else EventDatePollUiModel(id, dateString, timeString, Date(startTime), endTime?.let { Date(it) }, users.size, status)
    }

    private fun getPollDateTime(start: Long, end: Long?): Pair<AndroidString, AndroidString> {
        val startDate = Calendar.getInstance().apply { time = Date(start) }
        val currentDate = Calendar.getInstance()
        var showYear = startDate[Calendar.YEAR] != currentDate[Calendar.YEAR]

        // if end datetime is not present, only show start time
        if (end == null) return Date(start).let {
            AndroidString(text = it.toDateString(showYear)) to AndroidString(text = it.toTimeString())
        }

        // if end datetime is present
        // concat the dates together with a hyphen
        val endDate = Calendar.getInstance().apply { time = Date(end) }
        val startYearNotEqEndYear = startDate[Calendar.YEAR] != endDate[Calendar.YEAR]
        showYear = showYear || startYearNotEqEndYear
        val startDateTime = Date(start)
        val endDateTime = Date(end)
        return AndroidString(text = StringBuilder().apply {
                append(startDateTime.toDateString(showYear))
                if (startDate[Calendar.DAY_OF_YEAR] != endDate[Calendar.DAY_OF_YEAR]) {
                    append(" - ")
                    append(endDateTime.toDateString(showYear))
                }
            }.toString()) to AndroidString(text = "${startDateTime.toTimeString()} - ${endDateTime.toTimeString()}")
    }

    fun getDatePollItem(position: Int) = datePlan.datePolls[position]

    fun toggleDatePoll(position: Int) {
        if (!isUserAnOwner || (isUserAnOwner && interactor.event.itemInfo.datePollStatus)) {
            val originalStatus = datePlan.datePolls[position].status
            val originalCount = datePlan.datePolls[position].count
            observableDatePlan.value = datePlan.apply {
                datePlan.datePolls.apply {
                    this[position].apply {
                        if (status == UiModel.Status.POSITIVE) {
                            status = UiModel.Status.NEGATIVE
                            count -= 1
                        }
                        else {
                            status = UiModel.Status.POSITIVE
                            count += 1
                        }
                    }
                }
                itemsChangedIndices = listOf(position)
            }

            datePlan.datePolls[position].let {
                val isUpvoting = originalStatus == UiModel.Status.NEGATIVE
                interactor.updateDatePollCount(it.id, isUpvoting) {
                    when (it) {
                        is AsyncSuccessResult -> {
                            observableDatePlan.value = datePlan.apply {
                                datePlan.datePolls.apply {
                                    this[position].status = if (isUpvoting) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE
                                }
                                itemsChangedIndices = listOf(position)
                            }
                        }
                        is AsyncErrorResult -> {
                            observableDatePlan.value = datePlan.apply {
                                datePlan.datePolls.apply {
                                    this[position].apply {
                                        status = originalStatus
                                        count = originalCount
                                    }
                                }
                                itemsChangedIndices = listOf(position)
                            }
                        }
                    }
                }
            }
        }
        else {
            setDatePollSelected(position)
        }
    }

    fun getDatePollCount(): Int = datePlan.datePolls.size

    fun showDateTimeRangePicker(date: Date) {
        observableDateTimePicker.value = DateTimePickerUiModel(date, date)
    }

    fun addDatePoll(startDate: Date, endDate: Date?) {
        observableDateTimePicker.value = null
        observableDatePlan.value = datePlan.apply { status = UiModel.Status.LOADING }

        interactor.addDatePoll(startDate, endDate) {
            when (it) {
                is AsyncSuccessResult -> {
                    if (!interactor.event.itemInfo.datePollStatus && isUserAnOwner) {
                        refreshAndSelectDatePoll()
                    }
                    else {
                        refreshDatePolls(it.result)
                    }
                }
                is AsyncErrorResult -> {
                    observableDatePlan.value = datePlan.apply {
                        itemsChangedIndices = null
                        status = UiModel.Status.SUCCESS
                    }

                    handleError(it.error)
                }
            }
        }
    }

    fun cancelAddDatePoll() {
        observableDateTimePicker.value = null
    }

    fun getDatePolls() = datePlan.datePolls

    private fun refreshDatePollStatusButton(uiModel: ButtonUiModel) {
        observableDateVoteStatusButton.value = uiModel.let { button ->
            val event = interactor.event
            if (event.owners.contains(interactor.getCurrentUserId())) {
                if (event.itemInfo.datePollStatus) {
                    button.text = AndroidString(R.string.event_plan_close_vote)
                    button.status = UiModel.Status.NEGATIVE
                }
                else {
                    button.text = AndroidString(R.string.event_plan_open_vote)
                    button.status = UiModel.Status.POSITIVE
                }
            }
            else {
                button.text = null
                button.status = UiModel.Status.EMPTY
            }
            button
        }
    }

    private fun refreshDatePollSelectButton(uiModel: ButtonUiModel) {
        observableDateSelectButton.value = uiModel.let { button ->
            lastSelectedDatePollIndex.let {
                if (it == null) {
                    button.text = AndroidString(R.string.event_plan_date_no_selection)
                }
                else {
                    val selected = datePlan.datePolls[it]
                    val date = selected.date.text ?: ""
                    val time = if (!TextUtils.isEmpty(selected.time.text)) " (${selected.time.text})" else ""
                    button.text = AndroidString(R.string.event_plan_date_select, arrayOf(date, time))
                }
            }
            button
        }
    }

    fun toggleDatePollStatus() {
        observableViewAction.value = Loading(true)
        val pollIsOpened = interactor.event.itemInfo.datePollStatus

        interactor.setItemDatePollStatus(!pollIsOpened) {
            observableViewAction.value = Loading(false)
            when (it) {
                is AsyncSuccessResult -> {
                    refreshDatePollStatusButton(observableDateVoteStatusButton.value ?: ButtonUiModel(null))

                    // find out which date poll has the most vote, then preselect that
                    refreshAndSelectDatePoll()
                }
                is AsyncErrorResult -> {}
            }
        }
    }

    private fun refreshAndSelectDatePoll() {
        val polls = interactor.datePolls
        val maxIndex: Int? = if (polls.isNullOrEmpty()) null else {
            var temp = 0
            for (i in 1 until polls.size) {
                polls[i].let {
                    if (it.users.size > polls[temp].users.size) {
                        temp = i
                    }
                }
            }
            temp
        }
        lastSelectedDatePollIndex = maxIndex

        observableDatePlan.value = datePlan.apply {
            itemsChangedIndices = null
            status = UiModel.Status.SUCCESS
            datePolls.apply {
                clear()
                val event = interactor.event
                for (i in 0 until polls.size) {
                    add(polls[i].toUiModel(event, if (maxIndex != null && maxIndex == i) UiModel.Status.SUCCESS else null))     // success status means selected
                }
            }
        }

        refreshDatePollSelectButton(observableDateSelectButton.value ?: ButtonUiModel(null))
    }

    private fun setDatePollSelected(index: Int) {
        if (index != lastSelectedDatePollIndex) {
            observableDatePlan.value = datePlan.apply {
                itemsChangedIndices = ArrayList<Int>().apply {
                    add(index)
                    lastSelectedDatePollIndex?.let(::add)
                }
                status = UiModel.Status.SUCCESS
                datePolls[index].status = UiModel.Status.SUCCESS
                lastSelectedDatePollIndex?.let {
                    datePolls[it].status = UiModel.Status.NEGATIVE
                }
            }
            lastSelectedDatePollIndex = index

            refreshDatePollSelectButton(observableDateSelectButton.value ?: ButtonUiModel(null))
        }
    }

    fun setDateTimeFromPoll() {
        lastSelectedDatePollIndex?.let {
            observableViewAction.value = Loading(true)

            val selected = datePlan.datePolls[it]
            interactor.syncItemDate(selected.calendarStartDate, selected.calendarEndDate) {
                when (it) {
                    is AsyncSuccessResult -> {
                        val date = selected.date.text ?: ""
                        val time = if (!TextUtils.isEmpty(selected.time.text)) " (${selected.time.text})" else ""
                        observableViewAction.execute(arrayOf(
                                Loading(false),
                                Toast(AndroidString(
                                        resId = R.string.event_plan_date_select_completed,
                                        formatArgs = arrayOf(date, time))
                                )
                        ))
                    }
                    is AsyncErrorResult -> handleError(it.error)
                }
            }
        }
    }

    //endregion
    //region place poll

    fun loadPlacePolls() {
        if (isPlacePlanLoaded) return

        observablePlacePlan.value = placePlan.apply { status = UiModel.Status.LOADING }

        interactor.loadPlacePlan { it ->
            isPlacePlanLoaded = true

            when (it) {
                is AsyncSuccessResult -> {
                    if (!interactor.event.itemInfo.placePollStatus && isUserAnOwner) {
                        refreshAndSelectPlacePoll()
                    }
                    else {
                        refreshPlacePolls(it.result)
                    }
                }
                is AsyncErrorResult -> {
                    observablePlacePlan.value = placePlan.apply { status = UiModel.Status.ERROR }

                    handleError(it.error)
                }
            }
        }
    }

    private fun refreshPlacePolls(result: List<EventPlacePoll>) {
        observablePlacePlan.value = placePlan.apply {
            itemsChangedIndices = null
            status = UiModel.Status.SUCCESS
            placePolls.apply {
                clear()
                val event = interactor.event
                result.forEach {
                    add(it.toUiModel(event))
                }
            }
        }

        // fetch place info from added IDs
        loadPlaceInfo()
    }

    private fun loadPlaceInfo() {
        interactor.loadPollPlaceInfo {
            when (it) {
                is AsyncSuccessResult -> {
                    if (!it.result.isEmpty) {
                        observablePlacePlan.value = placePlan.apply {
                            itemsChangedIndices = ArrayList()
                            for (i in placePolls.indices) {
                                val poll = placePolls[i]
                                it.result[poll.id]?.let { place ->
                                    itemsChangedIndices?.add(i)

                                    poll.name = AndroidString(text = place.name)
                                    poll.address = AndroidString(text = place.address)
                                    poll.description = null
                                    poll.avatar = null
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

    private fun EventPlacePoll.toUiModel(event: EventItem, status: UiModel.Status? = null): EventPlacePollUiModel {
        val uiStatus: UiModel.Status = if (status == null) {
            val isPlacePollOpened = event.itemInfo.placePollStatus
            val isOwner = event.owners.contains(interactor.getCurrentUserId())
            val isUpvoted = users.contains(interactor.getCurrentUserId())
            val showUpvoted = if (!isUpvoted) false else !isOwner || (isOwner && isPlacePollOpened)
            if (showUpvoted) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE
        }
        else status
        return EventPlacePollUiModel(id,
                if (location.googlePlaceId != null) null else AndroidString(text = location.name),
                if (location.googlePlaceId != null) null else AndroidString(text = location.address),
                if (location.googlePlaceId != null) null else AndroidString(text = location.description),
                if (location.googlePlaceId != null) null else avatar,
                users.size,
                if (isAiSuggested) ButtonUiModel(AndroidString(R.string.event_plan_place_add), UiModel.Status.POSITIVE) else
                    ButtonUiModel(AndroidString(R.string.event_plan_place_added), UiModel.Status.NEGATIVE),
                uiStatus)
    }

    fun getPlacePollItem(position: Int) = placePlan.placePolls[position]

    fun getPlacePollCount(): Int = placePlan.placePolls.size

    private fun refreshAndSelectPlacePoll() {

    }

    fun togglePlacePoll(position: Int) {

    }

    fun togglePlacePollStatus() {

    }

    fun setPlaceFromPoll() {

    }

    fun viewPlaceDetails(position: Int) {

    }

    //endregion

    /**
     * Not applicable
     */
    override fun isViewEmpty(): Boolean = false

    //region observables

    fun getObservableBackground(): LiveData<String?> = observableBackground

    fun getObservableName(): LiveData<AndroidString> = observableName

    fun getObservableAttendees(): LiveData<List<UserUiModel>> = observableAttendees

    fun getObservableAttendeesLabel(): LiveData<AndroidString> = observableAttendeesLabel

    fun getObservableJoinButton(): LiveData<ButtonUiModel> = observableJoinButton

    fun getObservableNavigation(): LiveData<Navigation> = observableNavigation

    fun getObservableDatePlan(): LiveData<EventDatePlanUiModel> = observableDatePlan

    fun getObservableDateTimePicker(): LiveEvent<DateTimePickerUiModel> = observableDateTimePicker

    fun getObservableDateVoteStatusButton(): LiveData<ButtonUiModel> = observableDateVoteStatusButton

    fun getObservableDateSelectButton(): LiveData<ButtonUiModel> = observableDateSelectButton

    fun getObservablePlacePlan(): LiveData<EventPlacePlanUiModel> = observablePlacePlan

    //endregion
}