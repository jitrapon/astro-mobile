package io.jitrapon.glom.board.event

import android.text.TextUtils
import android.util.SparseArray
import androidx.util.set
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.User
import io.jitrapon.glom.base.repository.UserRepository
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemRepository
import io.jitrapon.glom.board.BoardRepository
import io.jitrapon.glom.board.event.autocomplete.EventAutocompleter
import io.jitrapon.glom.board.event.autocomplete.Suggestion
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor : EventAutocompleter.Callbacks {

    /**
     * TODO this will be an API instead of doing it client-side
     * Handles the logic to display useful suggestions to user to autocomplete the event name
     */
    private var autoCompleter: EventAutocompleter? = null

    /**
     * Callback for when auto-complete is available
     */
    private var autoCompleteCallback: ((List<Any>) -> Unit)? = null

    /**
     * Initialize board item to work with
     */
    fun setItem(item: BoardItem) {
        BoardItemRepository.setCache(item)
    }

    /**
     * Returns the cached board item
     */
    fun getItem(): BoardItem? {
        return BoardItemRepository.getCache()
    }

    /**
     * Must be called to initialize autocomplete feature with a callback
     */
    fun initializeNameAutocompleter(placeProvider: PlaceProvider, callback: (List<Any>) -> Unit) {
        autoCompleter = EventAutocompleter(this, placeProvider)
        autoCompleteCallback = callback
    }

    /**
     * Saves the current state
     */
    fun saveItem(info: EventInfo) {
        BoardItemRepository.save(info)
        // TODO get the field values from FIELD[NAME], FIELD[START_DATE], FIELD[PLACE]
        clearSuggestionCache()
    }

    /**
     * Returns list of loaded users, if available from specified IDs
     */
    fun getUsers(userIds: List<String>): List<User?>? {
        return ArrayList<User?>().apply {
            userIds.forEach {
                add(UserRepository.getById(it))
            }
        }
    }

    /**
     * Returns the currently signed in User object
     */
    fun getCurrentUser(): User? {
        return UserRepository.getCurrentUser()
    }

    /**
     * Joins the current user to an event
     *
     * @param statusCode - An int value for the new status (0 for DECLINED, 1 for MAYBE, 2 for GOING)
     */
    fun markEventAttendStatusForCurrentUser(itemId: String?, statusCode: Int, onComplete: ((AsyncResult<MutableList<String>?>) -> Unit)) {
        if (itemId == null) {
            onComplete(AsyncErrorResult(Exception("ItemId cannot be NULL")))
            return
        }
        val userId = UserRepository.getCurrentUser()?.userId
        if (TextUtils.isEmpty(userId)) {
            onComplete(AsyncErrorResult(Exception("Current user id cannot be NULL")))
            return
        }

        BoardRepository.getCache()?.let {
            Flowable.fromCallable {
                it.items.find { it.itemId == itemId && it is EventItem }
            }.flatMap {
                        when (statusCode) {
                            2 -> BoardRepository.joinEvent(userId!!, it.itemId)
                            else -> BoardRepository.declineEvent(userId!!, it.itemId)
                        }
                    }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        onComplete(AsyncSuccessResult(it.attendees))
                    }, {
                        onComplete(AsyncErrorResult(it))
                    }, {
                        //nothing yet
                    })
        }
    }

    //region auto suggestions callbacks

    /**
     * Callback when suggestions are available
     */
    override fun onSuggestionsAvailable(results: List<Any>) {
        autoCompleteCallback?.invoke(results)
    }

    //endregion
    //region autocomplete
    
    /* saved event fields */
    private val fields = SparseArray<Any?>(4)

    /* current locale supported */
    private var locale = Locale.ENGLISH

    /* locale-specific words to search for */
    private val timeConjunctions = listOf("on")
    private val placeConjunctions = listOf("at", "from", "to")
    private val peopleConjunctions = listOf("with", "for")
    private val nameSuggestions = listOf("Haircut", "Lunch", "Email", "Dinner", "Party", "Pick up package",
            "Pick up", "Pick up prescription", " Pick up dry cleaning", "Pick up cake", "Pick up kids")

    companion object {

        const val NAME = 0
        const val START_DATE = 1
        const val PLACE = 2
        const val INVITEES = 3
    }
    
    /**
     * Returns the last typed word
     */
    private fun getLastWord(text: String, delimiter: Char): String {
        return if (text.last() == delimiter) {
            text.dropLast(1).let {
                val lastSpaceIndex = it.lastIndexOf(delimiter)
                if (lastSpaceIndex != -1) it.substring(lastSpaceIndex, it.length).trim() else it
            }
        }
        else {
            val lastSpaceIndex = text.lastIndexOf(delimiter)
            if (lastSpaceIndex != -1) text.substring(lastSpaceIndex, text.length).trim() else text
        }
    }

    /**
     * Returns fields the user has not completely entered based on the input string so far
     */
    private fun getIncompleteFields(): List<Int> {
        return ArrayList<Int>().apply {
            (0 until 4)
                    .filter { fields.valueAt(it) == null }
                    .forEach { add(it) }
        }
    }

    /**
     * Process the query string and return the list of suggestions based on the query. This function
     * should be called in a background thread
     */
    fun filterSuggestions(text: String): List<Suggestion> {
        if (TextUtils.isEmpty(text)) return ArrayList()

        if (locale == Locale.ENGLISH) {

            // attempt to extract the last word in this text, see if it matches any of the conjunctions
            // in this locale
            val lastWord = getLastWord(text, ' ')

            // if the last word matches any of the conjunction, show suggestions based on that conjunction
            val suggestions = ArrayList<Suggestion>()
            timeConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                suggestions.addAll(listOf(
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24))),
                        Suggestion(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)))
                ))
            }
            placeConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                return ArrayList()
            }
            peopleConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                UserRepository.getAll()?.let {
                    suggestions.addAll(it.map { Suggestion(it) })
                }
            }

            // suggest for event names if it's not yet saved
            val emptyFields = getIncompleteFields()
            val names = ArrayList<Suggestion>().apply {
                if (suggestions.isEmpty()) {
                    val trimmed = text.trim()

                    // if the entire text so far matches any of the name suggestions, we can proceed to suggest
                    // other fields to enter
                    if (nameSuggestions.any { it.equals(trimmed, ignoreCase = true) }) {
                        if (emptyFields.any { it == START_DATE }) add(Suggestion("on", "When..?", true))
                        if (emptyFields.any { it == PLACE }) add(Suggestion("at", "Where..?", true))
                        if (emptyFields.any { it == INVITEES }) add(Suggestion("with", "With..?", true))
                    }

                    // otherwise, if we have not set any dates or places yet, show name suggestions that might fit with the query so far
                    if (emptyFields.any { it == START_DATE } && emptyFields.any {  it == PLACE }) {
                        addAll(
                                nameSuggestions.filter {
                                    it.startsWith(text, ignoreCase = true) && !it.equals(text, ignoreCase = true)
                                }.map {
                                            Suggestion(it)
                                        })
                    }
                }
            }

            fields[EventAutocompleter.NAME] = text.trim()
            debugLog()

            return ArrayList<Suggestion>().apply {
                addAll(names)
                addAll(suggestions)
            }
        }

        return ArrayList()
    }

    /**
     * Apply the current suggestion and update field
     */
    fun applySuggestion(currentText: String, selected: Suggestion) {
        selected.selectData.let {
            when (it) {
                is Date -> {
                    fields[START_DATE] = it
                    timeConjunctions.forEach {
                        fields[NAME] = currentText.replace(it, "", true).trim()
                    }
                }
                is Place -> {
                    fields[PLACE] = it
                }
                else -> { /* do nothing */ }
            }
        }
        debugLog()
    }

    fun removeSuggestion(removed: Suggestion) {
        if (fields[START_DATE] == removed) fields[START_DATE] = null
        if (fields[NAME] == removed) fields[NAME] = null
        if (fields[PLACE] == removed) fields[PLACE] = null
        debugLog()
    }

    private fun clearSuggestionCache() {
        fields.clear()
    }

    private fun debugLog() {
        AppLogger.i("name=${fields[NAME]}, " +
                "date=${fields[START_DATE]}, " +
                "place=${fields[PLACE]}, " +
                "invitees=${fields[INVITEES]}")
    }
    
    //endregion
}