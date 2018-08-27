package io.jitrapon.glom.board.event

import android.support.v4.util.ArrayMap
import android.text.TextUtils
import android.util.SparseArray
import androidx.util.set
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.datastructure.LimitedBooleanArray
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.User
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.PlaceInfo
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.Board
import io.jitrapon.glom.board.BoardDataSource
import io.jitrapon.glom.board.BoardItem
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor(private val userInteractor: UserInteractor, private val circleInteractor: CircleInteractor,
                          private val boardDataSource: BoardDataSource, private val eventItemDataSource: EventItemDataSource) {

    /* place provider use for providing place info */
    private var placeProvider: PlaceProvider? = null

    /* cached copy of the modified note text */
    private var note: String? = null

    /* flag to keep track of whether the information in this item has changed */
    private var isItemModified: Boolean = false

    /* whether or not initWith() has been called to initialize the item */
    private var initialized: Boolean = false

    /* convenient board instance */
    val board: Board
        get() = boardDataSource.getBoard(circleInteractor.getActiveCircleId(), BoardItem.TYPE_EVENT).blockingFirst()

    /* convenient event item instance */
    val event: EventItem
        get() = eventItemDataSource.getItem().blockingFirst()

    /* in-memory event date polls */
    val datePolls: List<EventDatePoll>
        get() = eventItemDataSource.getDatePolls(event, false).blockingFirst()

    /* in-memory event place polls */
    val placePolls: List<EventPlacePoll>
        get() = eventItemDataSource.getPlacePolls(event, false).blockingFirst()

    //region initializers

    /**
     * Sets a place provider instance. This can be instantiated from a class with access
     * to Context
     *
     * Sets a starting point for working with this item. Must be called before any other
     * functions
     */
    fun initWith(provider: PlaceProvider? = null, item: BoardItem? = null, retainModifiedStatus: Boolean = false) {
        if (provider != null && placeProvider == null) {
            placeProvider = provider
        }
        (item as? EventItem)?.let {
            if (!retainModifiedStatus) {
                isItemModified = false   // must be reset tit as EventItem)o false because the interactor instance is reused for new items
            }

            eventItemDataSource.initWith(it)
            note = it.itemInfo.note

            initialized = true
        }
    }

    /**
     * Returns true if initWith() has been called
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Returns true if the item initialized with initWith() has been modified
     */
    fun isItemModified() = isItemModified

    //endregion
    //region save operations

    /**
     * Saves the current state of the item and returns
     * the new item, along with a flag indicating if the item has been modified
     */
    fun saveItem(onComplete: (AsyncResult<Pair<BoardItem, Boolean>>) -> Unit) {
        Single.fromCallable {
            (event.itemInfo).apply {
                fields[NAME]?.let {
                    if (it is String) {
                        eventName = it
                    }
                }
                startTime = getSelectedDate()?.time ?: startTime
                location = getSelectedItemLocation()
                note = this@EventItemInteractor.note
            }
        }.flatMapCompletable {
            eventItemDataSource.saveItem(it)
        }.subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    clearSuggestionCache()
                    val temp = isItemModified
                    isItemModified = false
                    onComplete(AsyncSuccessResult(event to temp))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    //endregion
    //region location

    /**
     * Retrieves place information
     */
    fun loadPlaceInfo(customName: String?, onComplete: (AsyncResult<EventLocation>) -> Unit) {
        val placeId = event.itemInfo.location?.googlePlaceId
        if (!TextUtils.isEmpty(placeId)) {
            placeProvider?.let {
                it.getPlaces(arrayOf(placeId!!))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            if (it.isNotEmpty()) {
                                it.first().let {
                                    onComplete(AsyncSuccessResult(
                                            EventLocation(
                                                    it.latLng.latitude,
                                                    it.latLng.longitude,
                                                    it.id,
                                                    "g-place_id",
                                                    if (!TextUtils.isEmpty(customName)) customName else it.name.toString(),
                                                    it.address.toString())))
                                }
                            }
                            else {
                                onComplete(AsyncErrorResult(Exception("No places returned for id '$placeId'")))
                            }
                        }, {
                            onComplete(AsyncErrorResult(it))
                        })
            }
        }
    }

    fun getItemLocation(): EventLocation? {
        return event.itemInfo.location
    }

    private fun getSelectedItemLocation(): EventLocation? {
        return fields[LOCATION].let {
            when (it) {
                is CharSequence -> {
                    if (TextUtils.isEmpty(it)) null
                    else EventLocation(it.toString())
                }
                is EventLocation -> it
                else -> getItemLocation()
            }
        }
    }

    fun setItemLocation(locationText: CharSequence) {
        isItemModified = true

        fields[LOCATION] = locationText
    }

    fun setItemLocation(location: EventLocation?) {
        isItemModified = true

        event.itemInfo.let {
            it.location = location
            fields[LOCATION] = location
        }
    }

    //endregion
    //region datetime

    /**
     * Updates this cached event's date, processing whether the start and end dates are set correctly
     *
     * @return true iff the dates are correctly provided and set successfully
     */
    fun setItemDate(date: Date?, isStartDate: Boolean) {
        isItemModified = true

        event.itemInfo.let {
            if (isStartDate) {
                it.startTime = date?.time
                fields[START_DAY] = date
                fields[START_TIME] = date

                // if the new start date surpasses end date, reset the end date
                if (it.startTime != null && it.endTime != null) {
                    if (it.startTime!! > it.endTime!!) {
                        it.endTime = null
                    }
                }

                // if the new start date is null, also reset the end date
                if (it.startTime == null) {
                    it.endTime = null
                }
            }
            else {
                it.endTime = date?.time
                if (it.endTime != null && (it.startTime == null || it.startTime!! > it.endTime!!)) {
                    val newStartDate = Date(it.endTime!!).addHour(-1)
                    it.startTime = newStartDate.time
                    fields[START_DAY] = newStartDate
                    fields[START_TIME] = newStartDate
                }
            }
        }
    }

    fun getItemDate(startDate: Boolean): Date? {
        event.itemInfo.let {
            return if (startDate) it.startTime.let {
                if (it == null) null else Date(it)
            }
            else {
                it.endTime.let {
                    if (it == null) null else Date(it)
                }
            }
        }
    }

    fun syncItemDate(startDate: Date?, endDate: Date?, onComplete: (AsyncResult<Unit>) -> Unit) {
        eventItemDataSource.setDate(event, startDate, endDate)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    isItemModified = true

                    onComplete(AsyncSuccessResult(Unit))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    //endregion
    //region attend status

    /**
     * Returns list of loaded users, if available from specified IDs
     */
    fun getUsers(userIds: List<String>): List<User?>? = userInteractor.getUsersFromIds(userIds)

    /**
     * Returns the currently signed in user ID
     */
    fun getCurrentUserId(): String? = userInteractor.getCurrentUserId()

    /**
     * Joins the current user to an event
     *
     * @param statusCode - An int value for the new status (0 for DECLINED, 1 for MAYBE, 2 for GOING)
     */
    fun setItemAttendStatus(itemId: String, statusCode: Int, onComplete: ((AsyncResult<List<String>?>) -> Unit)) {
        val userId = userInteractor.getCurrentUserId()
        if (TextUtils.isEmpty(userId)) {
            onComplete(AsyncErrorResult(Exception("Current user id cannot be NULL")))
            return
        }

        board.let {
            var item: EventItem? = null
            Single.fromCallable { it.items.find { it.itemId == itemId && it is EventItem } as EventItem }
                    .flatMapCompletable {
                        item = it
                        when (statusCode) {
                            2 -> eventItemDataSource.joinEvent(it)
                            else -> eventItemDataSource.leaveEvent(it)
                        }
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        onComplete(AsyncSuccessResult(item!!.itemInfo.attendees))
                    }, {
                        onComplete(AsyncErrorResult(it))
                    })
        }
    }

    fun setItemDetailAttendStatus(statusCode: Int, onComplete: ((AsyncResult<List<String>?>) -> Unit)) {
        val userId = userInteractor.getCurrentUserId()
        if (TextUtils.isEmpty(userId)) {
            onComplete(AsyncErrorResult(Exception("Current user id cannot be NULL")))
            return
        }

        val item = event
        when (statusCode) {
            2 -> eventItemDataSource.joinEvent(item)
            else -> eventItemDataSource.leaveEvent(item)
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    isItemModified = true

                    onComplete(AsyncSuccessResult(event.itemInfo.attendees))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    fun setItemDatePollStatus(open: Boolean, onComplete: ((AsyncResult<Unit>) -> Unit)) {
        eventItemDataSource.setDatePollStatus(event, open)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    isItemModified = true

                    onComplete(AsyncSuccessResult(Unit))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    //endregion
    //region note

    fun setItemNote(newNote: String) {
        isItemModified = true
        note = newNote
    }

    //endregion
    //region date polls

    fun loadDatePlan(onComplete: (AsyncResult<List<EventDatePoll>>) -> Unit) {
        eventItemDataSource.getDatePolls(event, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(it))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                })
    }

    fun updateDatePollCount(id: String, upvote: Boolean, onComplete: (AsyncResult<Unit>) -> Unit) {
        eventItemDataSource.getDatePolls(event, false)
                .flatMapCompletable {
                    eventItemDataSource.updateDatePollCount(event, it.first { it.id == id }, upvote)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(Unit))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    fun addDatePoll(startDate: Date, endDate: Date?, onComplete: (AsyncResult<List<EventDatePoll>>) -> Unit) {
        eventItemDataSource.addDatePoll(event, startDate, endDate)
                .flatMap {
                    eventItemDataSource.getDatePolls(event, true)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(it))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    //endregion
    //region place polls

    fun loadPlacePlan(onComplete: (AsyncResult<List<EventPlacePoll>>) -> Unit) {
        eventItemDataSource.getPlacePolls(event, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(it))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                })
    }

    fun loadPollPlaceInfo(pollPlaceIdMap: ArrayMap<String, String>,
                          onLoadPlaceDetailsComplete: (AsyncResult<ArrayMap<String, Place>>) -> Unit) {
        if (placeProvider == null) {
            onLoadPlaceDetailsComplete(AsyncErrorResult(Exception("Place provider implementation is NULL")))
            return
        }
        val placeIds = pollPlaceIdMap.map { it.value }.toTypedArray()

        placeProvider?.let { placeProvider ->
            // load place details from place IDs
            placeProvider.getPlaces(placeIds)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        if (pollPlaceIdMap.size == result.size) {
                            onLoadPlaceDetailsComplete(AsyncSuccessResult(ArrayMap<String, Place>().apply {
                                for (i in result.indices) {
                                    put(pollPlaceIdMap.keyAt(i), result[i])
                                }
                            }))
                        }
                        else {
                            onLoadPlaceDetailsComplete(AsyncErrorResult(Exception("Failed to process result because" +
                                    " returned places array size (${result.size}) does not match requested item array size (${pollPlaceIdMap.size})")))
                        }
                    }, {
                        onLoadPlaceDetailsComplete(AsyncErrorResult(it))
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
     * Process the query string and return the list of place suggestions based on the query. This function
     * should be called in a background thread
     */
    fun filterLocationSuggestions(text: String): List<Suggestion> {
        return getPlaceSuggestions(text)
    }

    /**
     * Process the query string and return the list of suggestions based on the query. This function
     * should be called in a background thread
     */
    fun filterSuggestions(text: String): List<Suggestion> {
        isItemModified = true

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
            val showAllCustomPlaces = TextUtils.isEmpty(query) || placeConjunctions.contains(query)

            // add all places saved in this circle
            circleInteractor.getActiveCirclePlaces().let {
                if (it.isNotEmpty()) {
                    val places = if (showAllCustomPlaces) it else it.filter {
                        !TextUtils.isEmpty(it.name) && it.name!!.startsWith(query, true)
                    }
                    addAll(places.map { Suggestion(it) })
                }
            }

            // add places from Google Place API
            if (!showAllCustomPlaces) {
                placeProvider?.getAutocompletePrediction(query)?.blockingGet()?.let {
                    it.map {
                        Suggestion(PlaceInfo(it.getPrimaryText(null)?.toString(), it.getSecondaryText(null)?.toString(), null,
                                null, null, null, it.placeId, "g_place_id"))
                    }.let { addAll(it) }
                }
            }
        }
    }

    private fun getInviteeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            circleInteractor.getActiveUsersInCircle().let {
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