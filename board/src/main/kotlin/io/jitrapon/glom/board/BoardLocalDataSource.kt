package io.jitrapon.glom.board

import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.ContentChangeInfo
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.addDay
import io.jitrapon.glom.base.util.setTime
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventItemDao
import io.jitrapon.glom.board.item.event.EventItemFullEntity
import io.jitrapon.glom.board.item.event.calendar.CalendarDao
import io.jitrapon.glom.board.item.event.preference.EVENT_ITEM_MAX_FETCH_NUM_DAYS
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceDataSource
import io.jitrapon.glom.board.item.event.toEntity
import io.jitrapon.glom.board.item.event.toEventItems
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.ArrayList
import java.util.Date
import java.util.HashSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BoardLocalDataSource(
    database: BoardDatabase,
    private val userInteractor: UserInteractor,
    private val eventPrefDataSource: EventItemPreferenceDataSource,
    private val calendarDao: CalendarDao,
    override val contentChangeNotifier: PublishSubject<ContentChangeInfo>
) : BoardDataSource {

    /* synchronized lock for modifying in-memory board */
    private val lock: Any = Any()
    private lateinit var inMemoryBoard: Board

    /* previously fetched board item type */
    private var lastFetchedItemType: AtomicInteger = AtomicInteger(Integer.MIN_VALUE)

    /* DAO access object to event items in local DB */
    private val eventDao: EventItemDao by lazy { database.eventItemDao() }

    private var timestamp1 = System.currentTimeMillis()
    private var timestamp2 = System.currentTimeMillis()

    /* if set to true, the next time items are fetched, a sync operation with their respective
    source will trigger
     */
    private var requestSyncWithExternalSource: AtomicBoolean = AtomicBoolean(false)

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        // if this item type request has been requested before, just return the cached board version
        return if (lastFetchedItemType.get() == itemType && !requestSyncWithExternalSource.get()) Flowable.just(
            inMemoryBoard
        )

        // otherwise invoke the corresponding item DAO
        else {
            when (itemType) {
                BoardItem.TYPE_EVENT -> {
                    val requestSync = requestSyncWithExternalSource.get()
                    requestSyncWithExternalSource.set(false)
                    getEventBoard(circleId, requestSync)
                        .doOnNext {
                            synchronized(lock) {
                                inMemoryBoard = it
                            }
                            lastFetchedItemType.set(itemType)
                        }
                }
                else -> throw NotImplementedError()
            }
        }
    }

    override fun saveBoard(board: Board): Flowable<Board> {
        return Flowable.fromCallable {
            val eventEntities = ArrayList<EventItemFullEntity>()
            val updatedTime = Date().time
            board.items.forEach {
                when (it) {
                    is EventItem -> {
                        if (it.itemInfo.source.calendar == null) {
                            eventEntities.add(
                                it.toEntity(
                                    board.circleId,
                                    userInteractor.getCurrentUserId(),
                                    updatedTime
                                )
                            )
                        }
                    }
                    else -> { /* do nothing */
                    }
                }
            }
            if (eventEntities.isNotEmpty()) eventDao.insertOrReplaceEvents(*eventEntities.toTypedArray())

            // find items that should be deleted
            val itemsInDB = eventDao.getEventsInCircle(board.circleId).blockingGet()
            deleteItemsNotFoundInNewBoard(eventEntities, itemsInDB.toMutableList())
            board
        }.flatMap {
            getEventBoard(board.circleId, false)
        }.doOnNext {
            synchronized(lock) {
                inMemoryBoard = it
            }
        }
    }

    private fun getEventBoard(circleId: String, requestSync: Boolean): Flowable<Board> {
        return Flowable.zip(eventDao.getEventsInCircle(circleId).toFlowable()
            .doOnSubscribe { timestamp1 = System.currentTimeMillis() }
            .doOnNext { AppLogger.d("eventDao#getEventsInCircle took ${System.currentTimeMillis() - timestamp1} ms") }
            .subscribeOn(Schedulers.io()),
            getEventsFromDeviceCalendars(
                Date().setTime(0, 0),
                Date().addDay(EVENT_ITEM_MAX_FETCH_NUM_DAYS),
                requestSync
            )
                .doOnSubscribe { timestamp2 = System.currentTimeMillis() }
                .doOnNext {
                    AppLogger.d("getEventsFromDeviceCalendars took ${System.currentTimeMillis() - timestamp2} ms")

                    subscribeToCalendarUpdate()
                }
                .subscribeOn(Schedulers.io()),
            BiFunction<List<EventItemFullEntity>, List<EventItem>, Board> { dbEvents, deviceEvents ->
                val items = dbEvents.toEventItems(userInteractor.getCurrentUserId(), circleId)
                items.addAll(deviceEvents)
                Board(circleId, items, getSyncTime())
            })
    }

    private fun subscribeToCalendarUpdate() {
        calendarDao.registerUpdateObserver {

            // this will be invoked on a background thread
            contentChangeNotifier.onNext(ContentChangeInfo(false))
        }
    }

    private fun deleteItemsNotFoundInNewBoard(
        newItems: List<EventItemFullEntity>,
        itemsInDB: MutableList<EventItemFullEntity>
    ) {
        val newItemsIdSet: HashSet<String> = HashSet<String>().apply {
            newItems.forEach { add(it.entity.id) }
        }
        val iter = itemsInDB.iterator()
        while (iter.hasNext()) {
            iter.next().let {
                // remove items to be deleted that show up in the new list from remote, but also whose status is offline only
                if (newItemsIdSet.contains(it.entity.id) && it.entity.syncStatus == SyncStatus.OFFLINE.intValue) iter.remove()
            }
        }
        eventDao.deleteEvents(*itemsInDB.toTypedArray())
    }

    override fun createItem(circleId: String, item: BoardItem, remote: Boolean): Completable {
        return Completable.fromCallable {
            val requireReloadData = when (item) {
                is EventItem -> insertOrUpdateEvent(item, true)
                else -> throw NotImplementedError()
            }
            if (requireReloadData) {
                val board = when (item.itemType) {
                    BoardItem.TYPE_EVENT -> getEventBoard(circleId, false).blockingFirst()
                    else -> throw NotImplementedError()
                }
                synchronized(lock) {
                    inMemoryBoard = board
                }
            }
            else {
                synchronized(lock) {
                    inMemoryBoard.items.add(item)
                }
            }
        }
    }

    override fun editItem(circleId: String, item: BoardItem, remote: Boolean): Completable {
        // need to save this id so that it can be referenced later in case the item ID has changed
        val itemId = item.itemId
        return Completable.fromCallable {
            val requireReloadData = when (item) {
                is EventItem -> {
                    insertOrUpdateEvent(item, false)
                }
                else -> throw NotImplementedError()
            }
            if (requireReloadData) {
                val board = when (item.itemType) {
                    BoardItem.TYPE_EVENT -> getEventBoard(circleId, false).blockingFirst()
                    else -> throw NotImplementedError()
                }
                synchronized(lock) {
                    inMemoryBoard = board
                }
            }
            else {
                synchronized(lock) {
                    inMemoryBoard.items.indexOfFirst { it.itemId == itemId }.let {
                        if (it != -1) {
                            inMemoryBoard.items[it] = item
                        }
                    }
                }
            }
        }
    }

    override fun deleteItem(circleId: String, itemId: String, remote: Boolean): Completable {
        return Completable.fromCallable {
            synchronized(lock) {
                inMemoryBoard.items.indexOfFirst { itemId == it.itemId }.let {
                    if (it != -1) {
                        when (val item = inMemoryBoard.items[it]) {
                            is EventItem -> if (item.itemInfo.source.calendar == null) {
                                eventDao.deleteEventById(itemId)
                            }
                            else {
                                calendarDao.deleteEvent(item)
                            }
                            else -> { /* do nothing */
                            }
                        }
                        inMemoryBoard.items.removeAt(it)
                    }
                }
            }
        }
    }

    override fun setItemSyncStatus(itemId: String, status: SyncStatus): Completable {
        return Completable.fromAction {
            synchronized(lock) {
                val item = inMemoryBoard.items.firstOrNull { itemId == it.itemId }
                when (item) {
                    is EventItem -> if (item.itemInfo.source.calendar == null) {
                        eventDao.updateSyncStatusById(itemId, status.intValue)
                    }
                    else -> { /* unsupported */
                    }
                }
                item?.syncStatus = status
            }
        }
    }

    override fun getSyncTime(): Date = Date()

    private fun getEventsFromDeviceCalendars(
        startSearchTime: Date,
        endSearchTime: Date?,
        requestSync: Boolean
    ): Flowable<List<EventItem>> {
        return eventPrefDataSource.getSyncedCalendars()
            .map { calendars ->
                val result = ArrayList<EventItem>()
                try {
                    result.addAll(
                        calendarDao.getEventsSync(
                            calendars,
                            startSearchTime.time,
                            endSearchTime?.time,
                            requestSync
                        )
                    )
                }
                catch (ex: Exception) {
                    AppLogger.e(ex)
                }
                eventPrefDataSource.clearCalendarDiff()
                result
            }
    }

    /**
     * Insert or update an event, returns true iff the action modifies more than
     * just the item inserted or updated
     */
    private fun insertOrUpdateEvent(item: EventItem, isNew: Boolean): Boolean {
        val oldSource = item.itemInfo.source
        val newSource = item.itemInfo.newSource
        var areItemsModified = false
        if (oldSource.isBoard()) {

            // case 1: current source is this board, and new source is a device calendar
            if (newSource?.calendar != null) {
                if (!isNew) eventDao.deleteEventById(item.itemId)
                areItemsModified = calendarDao.createEvent(item, newSource.calendar)
            }

            // case 2: both current source and new source are this board
            else {
                eventDao.insertOrReplaceEvents(
                    item.toEntity(
                        inMemoryBoard.circleId,
                        userInteractor.getCurrentUserId(),
                        Date().time
                    )
                )
            }
        }
        else if (oldSource.calendar != null) {

            // case 3: current source is a device calendar, and new source is this board
            if (newSource != null && newSource.isBoard()) {
                eventDao.insertOrReplaceEvents(
                    item.toEntity(
                        inMemoryBoard.circleId,
                        userInteractor.getCurrentUserId(),
                        Date().time
                    )
                )
                if (!isNew) {
                    areItemsModified = calendarDao.deleteEvent(item)
                }
            }

            // case 4: current source is a device calendar, and new source is another calendar
            else if (newSource?.calendar != null && oldSource.calendar.calId != newSource.calendar.calId) {
                areItemsModified = if (!isNew) calendarDao.updateEvent(item, newSource.calendar)
                else calendarDao.createEvent(item, newSource.calendar)
            }

            // case 5: current source is a device calendar, and new source is the same calendar
            else {
                areItemsModified = if (!isNew) calendarDao.updateEvent(item)
                else calendarDao.createEvent(item, oldSource.calendar)
            }
        }
        item.apply {
            itemInfo.source = itemInfo.newSource ?: itemInfo.source
            itemInfo.newSource = null
        }
        return areItemsModified
    }

    override fun cleanUpContentChangeNotifier() {
        calendarDao.unregisterUpdateObserver()
    }
}
