package io.jitrapon.glom.board

import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * ViewModel class that controls EventItemActivity
 *
 * Created by Jitrapon
 */
class EventItemViewModel : BaseViewModel() {

    /* interactor for event item */
    private lateinit var interactor: EventItemInteractor

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
     * Returns whether or not autocomplete on the name text should be enabled
     */
    fun shouldShowNameAutocomplete(): Boolean = shouldShowNameAutocomplete

    /**
     * If initializeNameAutocompleter() is called, call this function to update the name text
     */
    fun updateName(string: String) {
        interactor.updateName(string)
    }

    //region view states

    /**
     * Indicates whether or not this view has items or not
     */
    override fun isViewEmpty(): Boolean = false

    //endregion
}