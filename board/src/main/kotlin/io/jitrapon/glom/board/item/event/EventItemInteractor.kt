package io.jitrapon.glom.board.item.event

import android.text.TextUtils
import android.util.SparseArray
import androidx.annotation.WorkerThread
import androidx.core.util.set
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.datastructure.LimitedBooleanArray
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.User
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.PlaceInfo
import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.addDay
import io.jitrapon.glom.base.util.addMinute
import io.jitrapon.glom.base.util.isToday
import io.jitrapon.glom.base.util.roundToNextHalfHour
import io.jitrapon.glom.base.util.setTime
import io.jitrapon.glom.base.viewmodel.runAsync
import io.jitrapon.glom.board.Board
import io.jitrapon.glom.board.BoardDataSource
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.event.exceptions.SaveOptionRequiredException
import io.jitrapon.glom.board.item.event.plan.EventDatePoll
import io.jitrapon.glom.board.item.event.plan.EventPlacePoll
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceDataSource
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Locale

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor(
    private val userInteractor: UserInteractor,
    private val circleInteractor: CircleInteractor,
    private val boardDataSource: BoardDataSource,
    private val eventItemDataSource: EventItemDataSource,
    private val eventItemPreferenceDataSource: EventItemPreferenceDataSource
) : BaseInteractor() {

    enum class SaveOption {
        THIS_OCCURRENCE, ALL_OCCURRENCE, ALL_OCCURRENCE_AFTER
    }

    /* place provider use for providing place info */
    private var placeProvider: PlaceProvider? = null

    /* flag to keep track of whether the information in this item has changed */
    private var isItemModified: Boolean = false

    /* whether or not initWith() has been called to initialize the item */
    private var initialized: Boolean = false

    /* convenient board instance */
    val board: Board
        get() = boardDataSource.getBoard(
            circleInteractor.getActiveCircleId(),
            BoardItem.TYPE_EVENT
        ).blockingFirst()

    /* convenient event item instance */
    val event: EventItem
        get() = eventItemDataSource.getItem().blockingFirst()

    /* in-memory event date polls */
    val datePolls: List<EventDatePoll>
        get() = eventItemDataSource.getDatePolls(event, false).blockingFirst()

    /* in-memory event place polls */
    val placePolls: List<EventPlacePoll>
        get() = eventItemDataSource.getPlacePolls(event, false).blockingFirst()

    val circleId: String?
        get() = circleInteractor.getActiveCircleId()

    /* whether or not the current user ID owns this item */
    private var isUserAnOwner: Boolean = false

    /* whether or not the current item is editable */
    private var isItemEditable: Boolean = false

    //region initializers

    /**
     * Sets a place provider instance. This can be instantiated from a class with access
     * to Context
     *
     * Sets a starting point for working with this item. Must be called before any other
     * functions
     */
    fun initWith(
        provider: PlaceProvider? = null,
        item: BoardItem? = null,
        retainModifiedStatus: Boolean = false
    ) {
        if (provider != null && placeProvider == null) {
            placeProvider = provider
        }
        (item as? EventItem)?.let {
            if (!retainModifiedStatus) {
                isItemModified =
                    false   // must be reset tit as EventItem)o false because the interactor instance is reused for new items
            }

            eventItemDataSource.initWith(it)
            isUserAnOwner = item.owners.contains(userId)
            initialized = true
            isItemEditable = it.isEditable
        }
    }

    /**
     * Returns true if the item initialized with initWith() has been modified
     */
    fun isItemModified() = isItemModified

    /**
     * Whether or not this user is an owner
     */
    fun isOwner() = isUserAnOwner

    /**
     * Whether or not the user can select a new date from a date poll
     */
    fun canUpdateDateTimeFromPoll() = !event.itemInfo.datePollStatus && isUserAnOwner

    /**
     * Whether or not the user can update the date poll count
     */
    fun canUpdateDatePollCount() =
        !isUserAnOwner || (isUserAnOwner && event.itemInfo.datePollStatus)

    /**
     * Whether or not the user can select a new place from a place poll
     */
    fun canUpdatePlaceFromPoll() = !event.itemInfo.placePollStatus && isUserAnOwner

    /**
     * Whether or not the user can update the place poll count
     */
    fun canUpdatePlacePollCount() =
        !isUserAnOwner || (isUserAnOwner && event.itemInfo.placePollStatus)

    //endregion
    //region title

    /**
     * Sets the event name, and optionally filter suggestions asynchronously
     */
    @WorkerThread
    fun setItemName(name: String, filter: Boolean): List<Suggestion> {
        val suggestions = ArrayList<Suggestion>()
        if (!isItemEditable) return suggestions

        isItemModified = true

        event.itemInfo.repeatInfo?.let {
            it.isReschedule = isItemModified
        }

        eventItemDataSource.setName(name)
        return suggestions
    }

    //endregion
    //region save operations

    /**
     * Saves the current state of the item and returns
     * the new item, along with a flag indicating if the item has been modified
     */
    fun saveItem(option: SaveOption?, isNewItem: Boolean, onComplete: (AsyncResult<Pair<BoardItem, Boolean>>) -> Unit) {
        // if this item is a calendar item, and the end date is not set by the user
        // set it to be equal to the start date so that the calendar provider allows
        // us to save
        event.itemInfo.apply {
            if ((source.calendar != null || newSource?.calendar != null) && endTime == null) {
                endTime = startTime.let {
                    if (it == null) null
                    else {
                        if (isFullDay) {
                            // we need to handle the standard of full-day events
                            // end date time actually one day ahead of actual date
                            Date(it).addDay(1).setTime(7, 0).time
                        }
                        else startTime
                    }
                }
            }
        }

        if (event.itemInfo.repeatInfo != null && option == null && !isNewItem) {
            onComplete(AsyncErrorResult(SaveOptionRequiredException()))
        }
        else {
            onComplete(AsyncSuccessResult(event to isItemModified))
            isItemModified = false
        }
    }

    //endregion
    //region location

    /**
     * Retrieves place information
     */
    fun loadPlaceInfo(customName: String?, onComplete: (AsyncResult<EventLocation>) -> Unit) {
        val placeId = event.itemInfo.location?.googlePlaceId
        if (!TextUtils.isEmpty(placeId)) {
            placeProvider?.getPlaces(arrayOf(placeId!!))
                ?.retryWhen(::errorIsUnauthorized)
                ?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe({ places ->
                    if (places.isNotEmpty()) {
                        places.first().let { result ->
                            val location = EventLocation(
                                result.latLng?.latitude,
                                result.latLng?.longitude,
                                result.id,
                                null,
                                if (!TextUtils.isEmpty(customName)) customName else result.name.toString(),
                                null,
                                result.address.toString()
                            )
                            setItemLocation(location, false)
                            onComplete(AsyncSuccessResult(location))
                        }
                    }
                    else {
                        onComplete(AsyncErrorResult(Exception("No places returned for id '$placeId'")))
                    }
                }, { error ->
                    onComplete(AsyncErrorResult(error))
                })?.autoDispose()
        }
    }

    fun getItemLocation(): EventLocation? = event.itemInfo.location

    /**
     * Sets this event item location. The argument must be either a CharSequence or an EventLocation type
     */
    fun setItemLocation(location: Any?, shouldMarkItemModified: Boolean = true) {
        if (!isItemEditable) return

        if (shouldMarkItemModified) {
            isItemModified = true

            event.itemInfo.repeatInfo?.let {
                it.isReschedule = isItemModified
            }
        }

        val eventLocation = when (location) {
            is CharSequence -> EventLocation(location.toString())
            is EventLocation -> location
            else -> null
        }
        eventItemDataSource.setLocation(eventLocation)
    }

    //endregion
    //region datetime

    /**
     * Updates this cached event's date, processing whether the start and end dates are set correctly
     */
    fun setItemDate(startDate: Date?, endDate: Date?, isFullDay: Boolean) {
        if (!isItemEditable) return

        // if this item is a calendar event, the start date must not be null
        if (startDate == null && event.itemInfo.source.calendar != null) return

        val startDateTemp = startDate?.let {
            if (isFullDay) it.setTime(7, 0).time
            else it.time
        }
        var endDateTemp = endDate?.let {
            if (isFullDay) {
                // we need to handle the standard of full-day events
                // end date time actually one day ahead of actual date
                it.addDay(1).setTime(7, 0).time
            }
            else it.time
        }

        // if the new start date surpasses end date, reset the end date
        // or if the new start date is cleared, we should also reset the end date
        if (((startDateTemp != null && endDateTemp != null) &&
                    (startDateTemp > endDateTemp)) || startDateTemp == null
        ) {
            endDateTemp = null
        }
        val originalStartTime = event.itemInfo.startTime
        val originalIsFullDay = event.itemInfo.isFullDay
        isItemModified =
            !(originalStartTime == startDateTemp && event.itemInfo.endTime == endDateTemp && originalIsFullDay == event.itemInfo.isFullDay)
        eventItemDataSource.setDate(startDateTemp, endDateTemp, isFullDay)

        event.itemInfo.repeatInfo?.let {
            it.isReschedule = isItemModified
        }
    }

    fun getItemDate(startDate: Boolean): Date? {
        event.itemInfo.let { info ->
            return if (startDate) info.startTime.let {
                if (it == null) null else Date(it)
            }
            else {
                info.endTime.let {
                    if (it == null) null else Date(it)
                }
            }
        }
    }

    fun syncItemDate(startDate: Date?, endDate: Date?, onComplete: (AsyncResult<Unit>) -> Unit) {
        eventItemDataSource.setDateRemote(event, startDate, endDate)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                isItemModified = true

                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    //endregion
    //region source

    fun getSyncedAndWritableSources(onComplete: (AsyncResult<List<EventSource>>) -> Unit) {
        eventItemPreferenceDataSource.getSyncedSources()
            .map {
                it.filter { source -> source.isWritable() }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
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
    fun getCurrentUserId(): String? = userId

    /**
     * Joins the current user to an event
     *
     * @param statusCode - An int value for the new status (0 for DECLINED, 1 for MAYBE, 2 for GOING)
     */
    fun setItemAttendStatus(
        itemId: String,
        statusCode: Int,
        onComplete: ((AsyncResult<List<String>?>) -> Unit)
    ) {
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
                .retryWhen(::errorIsUnauthorized)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(item!!.itemInfo.attendees))
                }, {
                    onComplete(AsyncErrorResult(it))
                })
        }
    }

    fun setItemDetailAttendStatus(
        status: EventItemUiModel.AttendStatus,
        onComplete: ((AsyncResult<List<String>?>) -> Unit)
    ) {
        val item = event
        when (status) {
            EventItemUiModel.AttendStatus.GOING -> eventItemDataSource.joinEvent(item)
            EventItemUiModel.AttendStatus.DECLINED -> eventItemDataSource.leaveEvent(item)
            else -> throw NotImplementedError()
        }.retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                isItemModified = true

                onComplete(AsyncSuccessResult(event.itemInfo.attendees))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun setItemDatePollStatus(open: Boolean, onComplete: ((AsyncResult<Unit>) -> Unit)) {
        eventItemDataSource.setDatePollStatus(event, open)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                isItemModified = true

                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    //endregion
    //region note

    fun setItemNote(note: CharSequence) {
        if (!isItemEditable) return

        isItemModified = true

        eventItemDataSource.setNote(note.toString())
    }

    //endregion
    //region date polls

    fun loadDatePlan(onComplete: (AsyncResult<List<EventDatePoll>>) -> Unit) {
        eventItemDataSource.getDatePolls(event, true)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //nothing yet
            }).autoDispose()
    }

    fun updateDatePollCount(id: String, upvote: Boolean, onComplete: (AsyncResult<Unit>) -> Unit) {
        eventItemDataSource.getDatePolls(event, false)
            .flatMapCompletable {
                eventItemDataSource.updateDatePollCount(event, it.first { it.id == id }, upvote)
            }
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun addDatePoll(
        startDate: Date,
        endDate: Date?,
        onComplete: (AsyncResult<List<EventDatePoll>>) -> Unit
    ) {
        eventItemDataSource.addDatePoll(event, startDate, endDate)
            .flatMap {
                eventItemDataSource.getDatePolls(event, true)
            }
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    //endregion
    //region place polls

    fun loadPlacePlan(onComplete: (AsyncResult<List<EventPlacePoll>>) -> Unit) {
        eventItemDataSource.getPlacePolls(event, true)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }, {
                //nothing yet
            }).autoDispose()
    }

    fun loadPollPlaceInfo(
        pollPlaceIdMap: androidx.collection.ArrayMap<String, String>,
        onLoadPlaceDetailsComplete: (AsyncResult<androidx.collection.ArrayMap<String, Place>>) -> Unit
    ) {
        if (placeProvider == null) {
            onLoadPlaceDetailsComplete(AsyncErrorResult(Exception("Place provider implementation is NULL")))
            return
        }
        val placeIds = pollPlaceIdMap.map { it.value }.toTypedArray()

        placeProvider?.let { placeProvider ->
            // load place details from place IDs
            placeProvider.getPlaces(placeIds)
                .retryWhen(::errorIsUnauthorized)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (pollPlaceIdMap.size == result.size) {
                        onLoadPlaceDetailsComplete(AsyncSuccessResult(androidx.collection.ArrayMap<String, Place>().apply {
                            for (i in result.indices) {
                                val pollId = pollPlaceIdMap.keyAt(i)
                                val placeInfo = result[i]
                                put(pollId, placeInfo)
                                placePolls.firstOrNull { it.id == pollId }?.let {
                                    it.location = EventLocation(
                                        placeInfo.latLng?.latitude, placeInfo.latLng?.longitude,
                                        placeInfo.id, null,
                                        placeInfo.name, null,
                                        placeInfo.address
                                    )
                                }
                            }
                        }))
                    }
                    else {
                        onLoadPlaceDetailsComplete(
                            AsyncErrorResult(
                                Exception(
                                    "Failed to process result because" +
                                            " returned places array size (${result.size}) does not match requested item array size (${pollPlaceIdMap.size})"
                                )
                            )
                        )
                    }
                }, {
                    onLoadPlaceDetailsComplete(AsyncErrorResult(it))
                })
        }
    }

    fun addPlacePoll(
        googlePlaceId: String?,
        placeId: String?,
        onComplete: (AsyncResult<List<EventPlacePoll>>) -> Unit
    ) {
        eventItemDataSource.addPlacePoll(event, placeId, googlePlaceId)
            .flatMap {
                eventItemDataSource.getPlacePolls(event, true)
            }
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(it))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun updatePlacePollCount(id: String, upvote: Boolean, onComplete: (AsyncResult<Unit>) -> Unit) {
        eventItemDataSource.getPlacePolls(event, false)
            .flatMapCompletable {
                eventItemDataSource.updatePlacePollCount(event, it.first { it.id == id }, upvote)
            }
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun setItemPlacePollStatus(open: Boolean, onComplete: ((AsyncResult<Unit>) -> Unit)) {
        eventItemDataSource.setPlacePollStatus(event, open)
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                isItemModified = true

                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun syncItemPlace(id: String, onComplete: (AsyncResult<Unit>) -> Unit) {
        eventItemDataSource.getPlacePolls(event, false)
            .flatMapCompletable { polls ->
                polls.first { it.id == id }.let { poll ->
                    eventItemDataSource.updateLocation(event, poll.location, true)
                }
            }
            .retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                isItemModified = true

                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    fun updateItemPlace(
        itemId: String, placeName: String?, placeAddress: String?,
        latLng: LatLng?, placeId: String?, remote: Boolean, onComplete: (AsyncResult<Unit>) -> Unit
    ) {
        Single.fromCallable {
            board.items.find {
                it.itemId == itemId && it is EventItem
            } ?: throw Exception(
                "Item with item ID $itemId does " +
                        "not exist or does not satisfy criteria to be updated with new location"
            )
        }.flatMapCompletable {
            val location = (it as EventItem).itemInfo.location?.copy(
                latitude = latLng?.latitude,
                longitude = latLng?.longitude,
                name = placeName,
                address = placeAddress,
                placeId = placeId
            )
            eventItemDataSource.updateLocation(it, location, remote)
        }.retryWhen(::errorIsUnauthorized)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete(AsyncSuccessResult(Unit))
            }, {
                onComplete(AsyncErrorResult(it))
            }).autoDispose()
    }

    //endregion
    //region source

    fun setItemSource(eventSource: EventSource): EventSource {
        return eventSource.apply {
            if (eventSource != event.itemInfo.source) {
                isItemModified = true
            }
            eventItemDataSource.setSource(this)
        }
    }

    //endregion
    //region calendar view

    fun loadOccupiedDates(onComplete: (AsyncResult<HashMap<Date, List<EventItem>>>) -> Unit) {
        runAsync(::getOccupiedDates, {
            onComplete(AsyncSuccessResult(it))
        }, {
            onComplete(AsyncErrorResult(it))
        })
    }

    private fun getOccupiedDates(): HashMap<Date, List<EventItem>> {
        return HashMap<Date, List<EventItem>>().apply {
            board.items.filterIsInstance(EventItem::class.java)
                .groupBy {
                    it.itemInfo.startTime?.let { epochTimeMs ->
                        Date(epochTimeMs).setTime(hour = 0, minute = 0, second = 0, millisecond = 0)
                    }
                }.forEach { (date, events) ->
                    date?.let {
                        put(it, events)
                    }
                }
        }
    }

    //endregion
    //region recurrence

    fun setItemRecurrence(repeatInfo: RepeatInfo?) {
        isItemModified = true

        eventItemDataSource.setRepeatInfo(repeatInfo)
    }

    //region autocomplete

    fun clearSession() {
        placeProvider?.clearSession()
    }

    companion object {
        private const val FIELD_COUNT = 5 // this changes according to how many fields we have below
        private const val NAME = 0 // stores object of type String
        private const val START_DAY =
            1 // stores object of type Triple<Calendar.DAY_OF_MONTH, Boolean(true), Date>
        private const val START_TIME =
            2 // stores object of type Triple<Calendar.HOUR_OF_DAY, Boolean(true), Date>
        private const val LOCATION = 3 // stores object of type EventLocation
        private const val INVITEES = 4 // stores object of type List<User>
    }

    /* saved event fields */
    private val fields = SparseArray<Any?>(FIELD_COUNT)

    /* current locale supported */
    private var locale = Locale.ENGLISH

    /* locale-specific words to search for */
    private val timeConjunctions = listOf("on")
    private val placeConjunctions = listOf("at", "from", "to")
    private val peopleConjunctions = listOf("with", "for")
    private val nameSuggestions = listOf(
        "Haircut", "Lunch", "Email", "Dinner", "Party", "Pick up package",
        "Pick up", "Pick up prescription", " Pick up dry cleaning", "Pick up cake", "Pick up kids"
    )

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

    fun getSubQueryStartIndex(): Int = subQueryStartIndex

    private fun getDaySuggestions(query: String): List<Suggestion> {
        val now = Date()
        return ArrayList<Suggestion>().apply {
            for (dayOffset in 0..10) {
                add(
                    Suggestion(
                        Triple(
                            Calendar.DAY_OF_MONTH, true, now.addDay(dayOffset)
                        )
                    )
                )
            }
        }
    }

    private fun getTimeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            val startDay = fields[START_DAY] as Date
            val startingTime: Date =
                if (startDay.isToday()) startDay.roundToNextHalfHour() else startDay.setTime(7, 0)
            when {
                fields[START_TIME] == null -> (0..300 step 30).mapTo(this) {
                    Suggestion(
                        Triple(
                            Calendar.HOUR_OF_DAY, true, startingTime.addMinute(it)
                        )
                    )
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
                    val places = if (showAllCustomPlaces) it
                    else it.filter {
                        !TextUtils.isEmpty(it.name) && it.name!!.startsWith(query, true)
                    }
                    addAll(places.map { Suggestion(it) })
                }
            }

            // add places from Google Place API
            if (!showAllCustomPlaces) {
                placeProvider?.getAutocompletePrediction(query)?.blockingGet()?.let {
                    it.map {
                        Suggestion(
                            PlaceInfo(
                                it.getPrimaryText(null)?.toString(),
                                null,
                                it.getSecondaryText(null)?.toString(),
                                null,
                                null,
                                null,
                                it.placeId,
                                null
                            )
                        )
                    }.let { addAll(it) }
                }
            }
        }
    }

    private fun getInviteeSuggestions(query: String): List<Suggestion> {
        return ArrayList<Suggestion>().apply {
            circleInteractor.getActiveUsersInCircle().let {
                val users = if (TextUtils.isEmpty(query) || peopleConjunctions.contains(query)) it
                else it.filter {
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
    fun applySuggestion(
        currentText: String,
        selected: Suggestion,
        displayText: String,
        delimiter: String
    ) {
        selected.selectData.let {
            when (it) {
                is Triple<*, *, *> -> {
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
                    eventItemDataSource.setName(newText.trim())
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
                    eventItemDataSource.setName(newText.trim())
                }
                else -> { /* do nothing */
                }
            }
        }
        debugLog()
    }

    private fun String.replaceLast(
        toReplace: String,
        replacement: String,
        ignoreCase: Boolean = false
    ): String {
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
        AppLogger.i(
            "name=${event.itemInfo.eventName}, " +
                    "startDay=${fields[START_DAY]}, " +
                    "startHour=${fields[START_TIME]}, " +
                    "place=${fields[LOCATION]}, " +
                    "invitees=${fields[INVITEES]}"
        )
    }

    //endregion
}
