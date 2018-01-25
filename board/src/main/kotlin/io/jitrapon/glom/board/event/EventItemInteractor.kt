package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.component.LocalEventNameAutocompleter
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemRepository

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor : LocalEventNameAutocompleter.Callbacks {

    private lateinit var repository: BoardItemRepository

    init {
        repository = BoardItemRepository()
    }

    /**
     * TODO this will be an API instead of doing it client-side
     * Handles the logic to display useful suggestions to user to autocomplete the event name
     */
    private var autocompleter: LocalEventNameAutocompleter? = null

    /**
     * Initialize board item to work with
     */
    fun setItem(item: BoardItem) {
        repository.setCache(item)
    }

    /**
     * Returns the cached board item
     */
    fun getItem(): BoardItem? {
        return repository.getCache()
    }

    /**
     * Must be called to initialize autocomplete feature
     */
    fun initializeNameAutocompleter(placeProvider: PlaceProvider) {
        autocompleter = LocalEventNameAutocompleter(this, placeProvider)
    }

    /**
     * Call this method to update name letter-by-letter for autocompletion analysis
     */
    fun onNameChanged(text: String) {
        autocompleter?.updateText(text)
    }

    override fun onSuggestionsAvailable(results: List<Any>) {
        StringBuilder().apply {
            results.forEach {
                append("$it, ")
            }
        }
    }

    /**
     * Saves the current state
     */
    fun saveItem(info: EventInfo) {
        repository.save(info)
    }
}