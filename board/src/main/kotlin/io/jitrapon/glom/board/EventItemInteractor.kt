package io.jitrapon.glom.board

import io.jitrapon.glom.base.component.LocalEventNameAutocompleter
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.util.AppLogger

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor : LocalEventNameAutocompleter.Callbacks {

    /**
     * TODO this will be an API instead of doing it client-side
     * Handles the logic to display useful suggestions to user to autocomplete the event name
     */
    private var autocompleter: LocalEventNameAutocompleter? = null

    /**
     * Must be called to initialize autocomplete feature
     */
    fun initializeNameAutocompleter(placeProvider: PlaceProvider) {
        autocompleter = LocalEventNameAutocompleter(this, placeProvider)
    }

    /**
     * Perform analysis to the new text
     */
    fun updateName(text: String) {
        autocompleter?.updateText(text)
    }

    override fun onSuggestionsAvailable(results: List<Any>) {
        StringBuilder().apply {
            results.forEach {
                append("$it, ")
            }
            AppLogger.i("Suggestions: $this")
        }
    }
}