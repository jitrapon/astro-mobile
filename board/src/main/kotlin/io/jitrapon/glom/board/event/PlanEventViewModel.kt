package io.jitrapon.glom.board.event

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.isNullOrEmpty
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

    /* whether or not user is attending */
    private var isUserAttending: Boolean = false

    /* observable navigation event */
    private val observableNavigation = LiveEvent<Navigation>()

    init {
        BoardInjector.getComponent().inject(this)
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
                //TODO
            }
            else {
                if (item is EventItem) {
                    if (!interactor.isInitialized() || interactor.event.itemId != item.itemId) {
                        interactor.initWith(placeProvider, item)
                    }
                    item.itemInfo.let {
                        isUserAttending = it.attendees.contains(interactor.getCurrentUserId())
                        observableName.value = AndroidString(text = it.eventName)
                        observableAttendees.value = it.attendees.toUiModel()
                        observableAttendeesLabel.value = getAttendeesLabel(it.attendees)
                        observableJoinButton.value = getJoinButtonStatus()
                        observableBackground.value = "https://mir-s3-cdn-cf.behance.net/project_modules/1400/82cf1121015893.59b6dc9dc8015.jpg"
                    }
                }
            }
        }
    }

    fun saveItem() {
        observableNavigation.value = Navigation(Const.NAVIGATE_BACK, if (interactor.isItemModified()) interactor.event else null)
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

    //endregion
}