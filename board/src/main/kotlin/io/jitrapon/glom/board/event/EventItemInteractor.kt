package io.jitrapon.glom.board.event

import android.text.TextUtils
import android.util.SparseArray
import androidx.util.set
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.datastructure.LimitedBooleanArray
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.repository.CircleRepository
import io.jitrapon.glom.base.repository.UserRepository
import io.jitrapon.glom.base.util.*
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

    private var placeProvider: PlaceProvider? = null

    /**
     * Initializes the PlaceProvider class
     */
    fun setPlaceProvider(placeProvider: PlaceProvider) {
        this.placeProvider = placeProvider
    }

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
                startTime = getSelectedDate()?.time ?: startTime
                fields[LOCATION]?.let {
                    if (it is PlaceInfo) location =
                            EventLocation(it.latitude, it.longitude, it.googlePlaceId, it.placeId, it.name)
                }
            }
            BoardItemRepository.save(info)
            clearSuggestionCache()
            onComplete(AsyncSuccessResult(it))
        }
    }

    /**
     * Updates this cached event's date
     */
    fun setDate(date: Date, isStartDate: Boolean) {
        BoardItemRepository.getCache()?.itemInfo?.let {
            if (it is EventInfo) {
                if (isStartDate) {
                    it.startTime = date.time
                }
                else {
                    it.endTime = date.time
                }
            }
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
        private const val FIELD_COUNT       = 5 // this changes according to how many fields we have below
        private const val NAME              = 0 // stores object of type String
        private const val START_DAY         = 1 // stores object of type Triple<Calendar.DAY_OF_MONTH, Boolean(true), Date>
        private const val START_TIME        = 2 // stores object of type Triple<Calendar.HOUR_OF_DAY, Boolean(true), Date>
        private const val LOCATION          = 3 // stores object of type EventLocation
        private const val INVITEES          = 4 // stores object of type List<User>
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
    private val filterConditions = LimitedBooleanArray(FIELD_COUNT, 1)
    private var lastModifiedField: Int = -1
    private var subQueryStartIndex: Int = -1

    /**
     * Returns fields the user has not completely entered based on the input string so far
     */
    private fun getIncompleteFields(): List<Int> {
        return ArrayList<Int>().apply {
            (0 until FIELD_COUNT)
                    .filter { fields[it] == null }
                    .forEach { add(it) }
        }
    }

    /**
     * Process the query string and return the list of suggestions based on the query. This function
     * should be called in a background thread
     */
    fun filterSuggestions(text: String): List<Suggestion> {
        if (TextUtils.isEmpty(text)) return ArrayList()

        val suggestions = ArrayList<Suggestion>()
        
        if (locale == Locale.ENGLISH) {

            // ignore certain keywords and suggestions in the name
            var temp = text
            val delimiter = ' '
            ignoreElements.forEach {
                temp = temp.replace(it, "", true).trim()
            }
            fields[NAME] = temp.trim()
            debugLog()

            // attempt to extract the last word in this text, see if it matches any of the conjunctions
            // in this locale
            val lastWord = text.getLastWord(delimiter)

            // if the last word matches any of the conjunction, cache that word and filters suggestions based on that word
            filterConditions[START_DAY] = if (!filterConditions[START_DAY]) {
                timeConjunctions.any { it.equals(lastWord, ignoreCase = true) } && fields[START_DAY] == null } else true
            filterConditions[START_TIME] = if (!filterConditions[START_TIME]) fields[START_DAY] != null && fields[START_TIME] == null else true
            filterConditions[LOCATION] = if (!filterConditions[LOCATION])
                placeConjunctions.any { it.equals(lastWord, ignoreCase = true) } && fields[LOCATION] == null else true
            filterConditions[INVITEES] = if (!filterConditions[INVITEES])
                peopleConjunctions.any { it.equals(lastWord, ignoreCase = true) }  && fields[INVITEES] == null else true

            // check if the filter condition field has changed, if so, cache the index of the conjunction
            if (lastModifiedField != filterConditions.getLastModifiedIndex()) {
                lastModifiedField = filterConditions.getLastModifiedIndex()
                subQueryStartIndex = text.trim().lastIndex + 1
            }
            val query = if (subQueryStartIndex >= 0 && subQueryStartIndex < text.trim().length)
                text.trim().substring(subQueryStartIndex, text.trim().length).trim() else ""
            AppLogger.i("query='$query'")

            when {
                filterConditions[START_DAY] -> return getDaySuggestions(query)
                filterConditions[START_TIME] -> return getTimeSuggestions(query)
                filterConditions[LOCATION] -> return getPlaceSuggestions(query)
                filterConditions[INVITEES] -> return getInviteeSuggestions(query)
            }

            // suggest for event names if it's not yet saved
            val emptyFields = getIncompleteFields()
            val names = ArrayList<Suggestion>().apply {
                if (suggestions.isEmpty()) {
                    val trimmed = text.trim()

                    // if the entire text so far matches any of the name suggestions, or one of the fields are already filled
                    // we can proceed to suggest other fields to enter
                    if (nameSuggestions.any { it.equals(trimmed, ignoreCase = true) } ||
                            fields.size() > 1) {
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

            return ArrayList<Suggestion>().apply {
                addAll(names)
                addAll(suggestions)
            }
        }

        return ArrayList()
    }

    fun getSubQueryStartIndex(): Int = subQueryStartIndex

    private fun getDaySuggestions(query: String): List<Suggestion> {
        val now = Date()
        return ArrayList<Suggestion>().apply {
            for (dayOffset in 0..10) {
                add(Suggestion(Triple(
                        Calendar.DAY_OF_MONTH, true, now.addDay(dayOffset)))
                )
            }
        }
    }

    private fun getTimeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            val startDay = fields[START_DAY] as Date
            val startingTime: Date = if (startDay.isToday()) startDay.roundToNextHalfHour() else startDay.setTime(7, 0)
            when {
                fields[START_TIME] == null -> (0..300 step 30).mapTo(this) {
                    Suggestion(Triple(
                            Calendar.HOUR_OF_DAY, true, startingTime.addMinute(it)
                    ))
                }
            }
        }
    }

    private fun getPlaceSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            CircleRepository.getCache()?.let {

                val showAllCustomPlaces = TextUtils.isEmpty(query) || placeConjunctions.contains(query)

                // add all places saved in this circle
                val places = if (showAllCustomPlaces) it.places else it.places.filter {
                    !TextUtils.isEmpty(it.name) && it.name!!.startsWith(query, true)
                }
                addAll(places.map { Suggestion(it) })

                // add places from Google Place API
                if (!showAllCustomPlaces) {
                    try {
                        placeProvider?.getAutocompletePrediction(query)
                                ?.blockingGet()
                                ?.map {
                                    Suggestion(PlaceInfo(it.getPrimaryText(null)?.toString(), it.getSecondaryText(null)?.toString(),
                                            null, null, null, it.placeId, "g_place_id"))
                                }
                                ?.let {
                                    addAll(it)
                                }
                    }
                    catch (ex: Exception) {
                        AppLogger.e(ex)
                    }
                }
            }
        }
    }

    private fun getInviteeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            UserRepository.getAll()?.let {
                val users = if (TextUtils.isEmpty(query) || peopleConjunctions.contains(query)) it else it.filter {
                    !TextUtils.isEmpty(it.userName) && it.userName.startsWith(query, true)
                }
                addAll(users.map { Suggestion(it) })
            }
        }
    }

    fun getSelectedDate(): Date? {
        return fields[START_DAY]?.let {
            Calendar.getInstance().run {
                time = it as Date
                if (fields[START_TIME] != null) {
                    val cal = Calendar.getInstance()
                    cal.time = (fields[START_TIME] as Date)
                    set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                }
                time
            }
        }
    }

    /**
     * Apply the current suggestion and update field
     */
    fun applySuggestion(currentText: String, selected: Suggestion, displayText: String, delimiter: String) {
        selected.selectData.let {
            when (it) {
                is Triple<*,*,*> -> {
                    if (it.first == Calendar.DAY_OF_MONTH) {
                        if (it.second == true) {
                            fields[START_DAY] = it.third
                            filterConditions[START_DAY] = false
                        }
                    }
                    else {
                        if (it.second == true) {
                            fields[START_TIME] = it.third
                            filterConditions[START_TIME] = false
                        }
                    }

                    var newText = currentText
                    ignoreElements.forEach {
                        newText = newText.replace(it, "", true)
                    }
                    timeConjunctions.forEach {
                        newText = newText.replaceLast(it + delimiter, "", true)
                        ignoreElements.add(it + delimiter)
                    }
                    ignoreElements.add(displayText)
                    fields[NAME] = newText.trim()
                }
                is PlaceInfo -> {
                    fields[LOCATION] = it
                    filterConditions[LOCATION] = false

                    var newText = currentText
                    ignoreElements.forEach {
                        newText = newText.replace(it, "", true)
                    }
                    placeConjunctions.forEach {
                        newText = newText.replaceLast(it + delimiter, "", true)
                        ignoreElements.add(it + delimiter)
                    }
                    ignoreElements.add(displayText)
                    fields[NAME] = newText.trim()
                }
                else -> { /* do nothing */ }
            }
        }
        debugLog()
    }

    private fun String.replaceLast(toReplace: String, replacement: String, ignoreCase: Boolean = false): String {
        val start = lastIndexOf(toReplace, ignoreCase = ignoreCase)
        if (start == -1) return this
        return StringBuilder().let {
            it.append(substring(0, start))
            it.append(replacement)
            it.append(substring(start + toReplace.length))
            it.toString()
        }
    }

    fun removeSuggestion(removed: Suggestion) {
        debugLog()
    }

    private fun clearSuggestionCache() {
        fields.clear()
        ignoreElements.clear()
        filterConditions.clear()
        lastModifiedField = -1
        subQueryStartIndex = -1
    }

    private fun debugLog() {
        AppLogger.i("name=${fields[NAME]}, " +
                "startDay=${fields[START_DAY]}, " +
                "startHour=${fields[START_TIME]}, " +
                "place=${fields[LOCATION]}, " +
                "invitees=${fields[INVITEES]}")
    }
    
    //endregion
}