package io.jitrapon.glom.board.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
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

    /* observable date plan */
    private val observableDatePlan = MutableLiveData<EventDatePlanUiModel>()

    /* whether or not the current user ID owns this item */
    private var isUserAnOwner: Boolean = false

    /* whether or not date plan has been loaded */
    private var isDatePlanLoaded: Boolean = false

    init {
        BoardInjector.getComponent().inject(this)
        datePlan = EventDatePlanUiModel(ArrayList(), ButtonUiModel(null, UiModel.Status.EMPTY), null, UiModel.Status.EMPTY)
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
                    if (!interactor.isInitialized() || interactor.event.itemId != item.itemId) {
                        interactor.initWith(placeProvider, item)
                    }
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
                            itemChangedIndex = null
                            item.itemInfo.let {
                                if (isUserAnOwner) {
                                    pollStatusButton.apply {
                                        if (it.datePollStatus) {
                                            text = AndroidString(R.string.event_plan_close_vote)
                                            status = UiModel.Status.NEGATIVE
                                        }
                                        else {
                                            text = AndroidString(R.string.event_plan_open_vote)
                                            status = UiModel.Status.POSITIVE
                                        }
                                    }
                                }
                                else {
                                    pollStatusButton.status = UiModel.Status.EMPTY
                                }
                            }
                        }
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
                    observableDatePlan.value = datePlan.apply {
                        itemChangedIndex = null
                        status = UiModel.Status.SUCCESS
                        datePolls.apply {
                            clear()
                            it.result.forEach { add(it.toUiModel()) }
                        }
                    }
                }
                is AsyncErrorResult -> {
                    observableDatePlan.value = datePlan.apply { status = UiModel.Status.ERROR }

                    handleError(it.error)
                }
            }
        }
    }

    private fun EventDatePoll.toUiModel(): EventDatePollUiModel {
        val (dateString, timeString) = getPollDateTime(startTime, endTime)
        return EventDatePollUiModel(id, dateString, timeString, Date(startTime), endTime?.let { Date(it) }, users.size,
                if (users.contains(interactor.getCurrentUserId())) UiModel.Status.POSITIVE else UiModel.Status.NEGATIVE)
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
            itemChangedIndex = position
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
                            itemChangedIndex = position
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
                            itemChangedIndex = position
                        }
                    }
                }
            }
        }
    }

    fun getDatePollCount(): Int = datePlan.datePolls.size

    fun showDateTimeRangePicker(date: Date) {
        observableDateTimePicker.value = DateTimePickerUiModel(date, date)
    }

    fun addDatePoll(startDate: Date, endDate: Date?) {
        observableDatePlan.value = datePlan.apply { status = UiModel.Status.LOADING }

        interactor.addDatePoll(startDate, endDate) {
            when (it) {
                is AsyncSuccessResult -> {
                    observableDatePlan.value = datePlan.apply {
                        itemChangedIndex = null
                        status = UiModel.Status.SUCCESS
                        datePolls.apply {
                            clear()
                            it.result.forEach { add(it.toUiModel()) }
                        }
                    }
                }
                is AsyncErrorResult -> {
                    observableDatePlan.value = datePlan.apply {
                        itemChangedIndex = null
                        status = UiModel.Status.SUCCESS
                    }

                    handleError(it.error)
                }
            }
        }
    }

    //endregion
    //region place poll

    fun togglePlacePoll(position: Int) {

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

    //endregion
}