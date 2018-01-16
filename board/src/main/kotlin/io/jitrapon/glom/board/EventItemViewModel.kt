package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import java.util.*

/**
 * ViewModel class that controls EventItemActivity
 *
 * Created by Jitrapon
 */
class EventItemViewModel : BaseViewModel() {

    /* interactor for event item */
    private lateinit var interactor: EventItemInteractor

    /* Uimodel responsible for this event */
    private val observableEvent = MutableLiveData<EventItemUiModel>()
    private var eventItemUiModel: EventItemUiModel? = null
    private var eventItem: EventItem? = null

    /* indicates whether or not the view should display autocomplete */
    private var shouldShowNameAutocomplete: Boolean = true

    init {
        interactor = EventItemInteractor()
    }

    fun init(placeProvider: PlaceProvider) {
        if (shouldShowNameAutocomplete) {
            interactor.initializeNameAutocompleter(placeProvider)
        }
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
                    eventItem = item
                    observableEvent.value = item.toUiModel().apply {
                        eventItemUiModel = this
                    }
                }
                else {
                    AppLogger.w("Cannot set item because item is not an instance of EventItem")
                }
            }
        }
    }

    /**
     * Converts EventItem model to its UiModel counterpart
     */
    private fun EventItem.toUiModel(): EventItemUiModel {
        return EventItemUiModel(
                itemId,
                itemInfo.eventName,
                null,
                null,
                null,
                null,
                EventItemUiModel.AttendStatus.MAYBE
        )
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
     * Converts the current UiModel to Model
     */
    fun getCurrentBoardItem(name: String): EventItem? {
        return eventItemUiModel?.let {
            it.title = name
            it.toModel()
        }
    }

    /**
     * Converts an instance of UiModel to Model
     */
    private fun EventItemUiModel.toModel(): EventItem? {
        val uiModel = this
        return eventItem?.apply {
            updatedTime = Date().time
            itemInfo.eventName = uiModel.title
        }
    }

    //region view states

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = false

    //endregion
    //region observables

    fun getObservableEvent(): LiveData<EventItemUiModel> = observableEvent

    //endregion
}