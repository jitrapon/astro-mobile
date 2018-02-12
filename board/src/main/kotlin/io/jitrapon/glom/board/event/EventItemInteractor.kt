package io.jitrapon.glom.board.event

import android.text.TextUtils
import android.util.SparseArray
import androidx.util.set
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.User
import io.jitrapon.glom.base.repository.UserRepository
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.addDay
import io.jitrapon.glom.base.util.getLastWord
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemRepository
import io.jitrapon.glom.board.BoardRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor {

    /**
     * Initialize board item to work with
     */
    fun setItem(item: BoardItem) {
        BoardItemRepository.setCache(item)
    }

    /**
     * Saves the current state
     */
    fun saveItem(onComplete: (AsyncResult<BoardItem>) -> Unit) {
        BoardItemRepository.getCache()?.let {
            val info = (it.itemInfo as EventInfo).apply {
                fields[NAME]?.let { if (it is String) eventName = it }
            }
            BoardItemRepository.save(info)
            clearSuggestionCache()
            onComplete(AsyncSuccessResult(it))
        }
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

    //endregion
    //region autocomplete
    
    companion object {
        private const val FIELD_COUNT       = 7 // this changes according to how many fields we have below
        private const val NAME              = 0 // stores object of type String
        private const val START_DAY         = 1 // stores object of type Triple<Calendar.DAY_OF_MONTH, Boolean(true), Date>
        private const val START_HOUR        = 2 // stores object of type Triple<Calendar.HOUR_OF_DAY, Boolean(true), Date>
        private const val END_DAY           = 3 // stores object of type Triple<Calendar.DAY_OF_MONTH, Boolean(false), Date>
        private const val END_HOUR          = 4 // stores object of type Triple<Calendar.HOUR_OF_DAY, Boolean(false), Date>
        private const val LOCATION          = 5 // stores object of type EventLocation
        private const val INVITEES          = 6 // stores object of type List<User>
    }
    
    /* saved event fields */
    private val fields = SparseArray<Any?>(FIELD_COUNT)

    /* current locale supported */
    private var locale = Locale.ENGLISH

    /* locale-specific words to search for */
    private val timeConjunctions = listOf("on")
    private val placeConjunctions = listOf("at", "from", "to")
    private val peopleConjunctions = listOf("with", "for")
    private val nameSuggestions = listOf("Haircut", "Lunch", "Email", "Dinner", "Party", "Pick up package",
            "Pick up", "Pick up prescription", " Pick up dry cleaning", "Pick up cake", "Pick up kids")

    private var ignoreElements = ArrayList<String>()

    
    /**
     * Returns fields the user has not completely entered based on the input string so far
     */
    private fun getIncompleteFields(): List<Int> {
        return ArrayList<Int>().apply {
            (0 until FIELD_COUNT)
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
            val lastWord = text.getLastWord(' ')

            // if the last word matches any of the conjunction, show suggestions based on that conjunction
            val suggestions = ArrayList<Suggestion>()
            if (fields[START_DAY] == null) {
                val now = Date()
                timeConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                    for (dayOffset in 0..10) {
                        suggestions.add(Suggestion(Triple(Calendar.DAY_OF_MONTH, true, now.addDay(dayOffset))))
                    }
                }
            }
            // TODO CHECK IF fields[START_DAY] != null && lastWord is timeConjunction

            if (fields[LOCATION] == null) {
                placeConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                    return ArrayList()
                }
            }

            if (fields[INVITEES] == null) {
                peopleConjunctions.find { it.equals(lastWord, ignoreCase = true) }?.let {
                    UserRepository.getAll()?.let {
                        suggestions.addAll(it.map { Suggestion(it) })
                    }
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
                        if (emptyFields.any { it == START_DAY }) add(Suggestion("on", "When..?", true))
                        if (emptyFields.any { it == LOCATION }) add(Suggestion("at", "Where..?", true))
                        if (emptyFields.any { it == INVITEES }) add(Suggestion("with", "With..?", true))
                    }

                    // otherwise, if we have not set any dates or places yet, show name suggestions that might fit with the query so far
                    if (emptyFields.any { it == START_DAY } && emptyFields.any {  it == LOCATION }) {
                        addAll(nameSuggestions
                                .filter { it.startsWith(text, ignoreCase = true) && !it.equals(text, ignoreCase = true) }
                                .map { Suggestion(it) })
                    }
                }
            }

            // ignore certain keywords and suggestions in the name
            var temp = text
            ignoreElements.forEach {
                temp = temp.replace(it, "", true).trim()
            }
            fields[NAME] = temp.trim()
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
    fun applySuggestion(currentText: String, selected: Suggestion, displayText: String) {
        val delimiter = ' '
        selected.selectData.let {
            when (it) {
                is Date -> {
                    fields[START_DAY] = it
                    timeConjunctions.forEach {
                        val (newText, found) = currentText.replaceLast(it, "", true)
                        if (found) {
                            fields[NAME] = newText.trim()
                           ignoreElements.add(it + delimiter + displayText)
                        }
                    }
                }
                is Place -> {
                    fields[LOCATION] = it
                    placeConjunctions.forEach {
                        val (newText, found) = currentText.replaceLast(it, "", true)
                        if (found) {
                            fields[NAME] = newText.trim()
                            ignoreElements.add(it + delimiter + displayText)
                        }
                    }
                }
                else -> { /* do nothing */ }
            }
        }
        debugLog()
    }

    private fun String.replaceLast(toReplace: String, replacement: String, ignoreCase: Boolean = false): Pair<String, Boolean> {
        val start = lastIndexOf(toReplace, ignoreCase = ignoreCase)
        if (start == -1) return this to false
        return StringBuilder().let {
            it.append(substring(0, start))
            it.append(replacement)
            it.append(substring(start + toReplace.length))
            it.toString() to true
        }
    }

    fun removeSuggestion(removed: Suggestion) {
        debugLog()
    }

    private fun clearSuggestionCache() {
        fields.clear()
    }

    private fun debugLog() {
        AppLogger.i("name=${fields[NAME]}, " +
                "startDay=${fields[START_DAY]}, " +
                "startHour=${fields[START_HOUR]}, " +
                "endDay=${fields[END_DAY]}, " +
                "endHour=${fields[END_HOUR]}, "+
                "place=${fields[LOCATION]}, " +
                "invitees=${fields[INVITEES]}")
    }
    
    //endregion
}