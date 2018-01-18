package io.jitrapon.glom.board

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * Manages all view states in the Event detail screen
 *
 * Created by Jitrapon
 */
class EventItemViewModel : BaseViewModel() {

    private lateinit var interactor: EventItemInteractor

    private var prevName: String? = null
    private val observableName = MutableLiveData<String>()

    /* indicates whether or not the view should display autocomplete */
    private var shouldShowNameAutocomplete: Boolean = true

    init {
        interactor = EventItemInteractor()
    }

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
}