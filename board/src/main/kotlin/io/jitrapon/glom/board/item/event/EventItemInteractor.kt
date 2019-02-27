package io.jitrapon.glom.board.item.event

import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.User
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.util.addHour
import io.jitrapon.glom.board.Board
import io.jitrapon.glom.board.BoardDataSource
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.event.plan.EventDatePoll
import io.jitrapon.glom.board.item.event.plan.EventPlacePoll
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList
import java.util.Date

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor(private val userInteractor: UserInteractor, private val circleInteractor: CircleInteractor,
                          private val boardDataSource: BoardDataSource, private val eventItemDataSource: EventItemDataSource): BaseInteractor() {

    /* place provider use for providing place info */
    private var placeProvider: PlaceProvider? = null

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

    /* whether or not the current user ID owns this item */
    private var isUserAnOwner: Boolean = false

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
            isUserAnOwner = item.owners.contains(userId)
            initialized = true
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
    fun canUpdateDatePollCount() = !isUserAnOwner || (isUserAnOwner && event.itemInfo.datePollStatus)

    /**
     * Whether or not the user can select a new place from a place poll
     */
    fun canUpdatePlaceFromPoll() = !event.itemInfo.placePollStatus && isUserAnOwner

    /**
     * Whether or not the user can update the place poll count
     */
    fun canUpdatePlacePollCount() = !isUserAnOwner || (isUserAnOwner && event.itemInfo.placePollStatus)

    //endregion
    //region title

    /**
     * Sets the event name, and optionally filter suggestions asynchronously
     */
    @WorkerThread
    fun setItemName(name: String, filter: Boolean): List<Suggestion> {
        isItemModified = true

        eventItemDataSource.setName(name)
        return ArrayList()
    }

    //endregion
    //region save operations

    /**
     * Saves the current state of the item and returns
     * the new item, along with a flag indicating if the item has been modified
     */
    fun saveItem(onComplete: (AsyncResult<Pair<BoardItem, Boolean>>) -> Unit) {
        Single.fromCallable {
            event.itemInfo.apply {

            }
        }.flatMapCompletable {
            eventItemDataSource.saveItem(it)
        }.retryWhen(::errorIsUnauthorized)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val temp = isItemModified
                    isItemModified = false
                    onComplete(AsyncSuccessResult(event to temp))
                }, {
                    onComplete(AsyncErrorResult(it))
                }).autoDispose()
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
                                result.latLng.latitude,
                                result.latLng.longitude,
                                result.id,
                                null,
                                if (!TextUtils.isEmpty(customName)) customName else result.name.toString(),
                                null,
                                result.address.toString())
                            setItemLocation(location)
                            onComplete(AsyncSuccessResult(location))
                        }
                    } else {
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
    fun setItemLocation(location: Any?) {
        isItemModified = true

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
    fun setItemDate(date: Date?, isStartDate: Boolean) {
        isItemModified = true

        if (isStartDate) {
            val startDateTemp = date?.time
            var endDateTemp = event.itemInfo.endTime

            // if the new start date surpasses end date, reset the end date
            // or if the new start date is null, we should also reset the end date
            if (((startDateTemp != null && endDateTemp != null) && (startDateTemp >= endDateTemp)) || startDateTemp == null) {
                endDateTemp = null
            }
            eventItemDataSource.setDate(startDateTemp, endDateTemp)
        }
        else {
            var startDateTemp = event.itemInfo.startTime
            val endDateTemp = date?.time

            // we should set the start time accordingly to one hour prior to the new end time
            // if it is less than the start time already set, or if the start time has not been set
            if (endDateTemp != null && (startDateTemp == null || startDateTemp >= endDateTemp)) {
                startDateTemp = Date(endDateTemp).addHour(-1).time
            }
            eventItemDataSource.setDate(startDateTemp, endDateTemp)
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
    fun setItemAttendStatus(itemId: String, statusCode: Int, onComplete: ((AsyncResult<List<String>?>) -> Unit)) {
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

    fun setItemDetailAttendStatus(statusCode: Int, onComplete: ((AsyncResult<List<String>?>) -> Unit)) {
        val item = event
        when (statusCode) {
            2 -> eventItemDataSource.joinEvent(item)
            else -> eventItemDataSource.leaveEvent(item)
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
                .flatMapCompletable { polls ->
                    eventItemDataSource.updateDatePollCount(event, polls.first { it.id == id }, upvote)
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

    fun addDatePoll(startDate: Date, endDate: Date?, onComplete: (AsyncResult<List<EventDatePoll>>) -> Unit) {
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

    fun loadPollPlaceInfo(pollPlaceIdMap: androidx.collection.ArrayMap<String, String>,
                          onLoadPlaceDetailsComplete: (AsyncResult<androidx.collection.ArrayMap<String, Place>>) -> Unit) {
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
                                        it.location = EventLocation(placeInfo.latLng.latitude, placeInfo.latLng.longitude,
                                                placeInfo.id, null, placeInfo.name?.toString(), null, placeInfo.address?.toString())
                                    }
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

    fun addPlacePoll(googlePlaceId: String?, placeId: String?, onComplete: (AsyncResult<List<EventPlacePoll>>) -> Unit) {
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
                        eventItemDataSource.setPlace(event, poll.location)
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

    //endregion
}
