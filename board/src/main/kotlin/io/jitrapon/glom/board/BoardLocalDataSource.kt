package io.jitrapon.glom.board

import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventItemDao
import io.jitrapon.glom.board.item.event.EventItemFullEntity
import io.jitrapon.glom.board.item.event.calendar.DeviceEvent
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceDataSource
import io.jitrapon.glom.board.item.event.toEntity
import io.jitrapon.glom.board.item.event.toEventItems
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList
import java.util.Date
import java.util.HashSet
import java.util.concurrent.atomic.AtomicInteger

class BoardLocalDataSource(database: BoardDatabase,
                           private val userInteractor: UserInteractor,
                           private val eventPref: EventItemPreferenceDataSource) : BoardDataSource {

    /* synchronized lock for modifying in-memory board */
    private val lock: Any = Any()
    private lateinit var inMemoryBoard: Board

    /* previously fetched board item type */
    private var lastFetchedItemType: AtomicInteger = AtomicInteger(Integer.MIN_VALUE)

    /* DAO access object to event items in local DB */
    private val eventDao: EventItemDao by lazy { database.eventItemDao() }

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        // if this item type request has been requested before, just return the cached board version
        return if (lastFetchedItemType.get() == itemType) Flowable.just(inMemoryBoard)

        // otherwise invoke the corresponding item DAO
        else {
            when (itemType) {
                BoardItem.TYPE_EVENT -> {
                    Flowable.zip(eventDao.getEventsInCircle(circleId).toFlowable().subscribeOn(Schedulers.io()),
                                getEventsFromDeviceCalendars().subscribeOn(Schedulers.io()),
                                BiFunction<List<EventItemFullEntity>, List<DeviceEvent>, Board> { dbEvents, deviceEvents ->
                                    val items = dbEvents.toEventItems(userInteractor.getCurrentUserId())
                                    items.addAll(deviceEvents.toEventItems())
                                    Board(circleId, items, getSyncTime())
                                })
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
                        if (!it.isDeviceEvent) {
                            eventEntities.add(it.toEntity(board.circleId, userInteractor.getCurrentUserId(), updatedTime))
                        }
                    }
                    else -> { /* do nothing */ }
                }
            }
            if (eventEntities.isNotEmpty()) eventDao.insertOrReplaceEvents(*eventEntities.toTypedArray())

            // find items that should be deleted
            val itemsInDB = eventDao.getEventsInCircle(board.circleId).blockingGet()
            deleteItemsNotFoundInNewBoard(eventEntities, itemsInDB.toMutableList())
            board
        }.doOnNext {
            synchronized(lock) {
                inMemoryBoard = it
            }
        }
    }

    private fun deleteItemsNotFoundInNewBoard(newItems: List<EventItemFullEntity>, itemsInDB: MutableList<EventItemFullEntity>) {
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

    override fun createItem(item: BoardItem, remote: Boolean): Completable {
        return Completable.fromCallable {
            when (item) {
                is EventItem -> eventDao.insertOrReplaceEvents(item.toEntity(inMemoryBoard.circleId, userInteractor.getCurrentUserId(), Date().time))
            }
        }.doOnComplete {
            synchronized(lock) {
                inMemoryBoard.items.add(item)
            }
        }
    }

    override fun editItem(item: BoardItem): Completable {
        return Completable.fromCallable {
            when (item) {
                is EventItem -> eventDao.insertOrReplaceEvents(item.toEntity(inMemoryBoard.circleId, userInteractor.getCurrentUserId(), Date().time))
            }
        }.doOnComplete {
            synchronized(lock) {
                inMemoryBoard.items.indexOfFirst { it.itemId == item.itemId }.let {
                    if (it != -1) {
                        inMemoryBoard.items[it] = item
                    }
                }
            }
        }
    }

    override fun deleteItem(itemId: String, remote: Boolean): Completable {
        return Completable.fromCallable {
            synchronized(lock) {
                inMemoryBoard.items.indexOfFirst { itemId == it.itemId }.let {
                    if (it != -1) {
                        val item = inMemoryBoard.items[it]
                        when (item) {
                            is EventItem -> eventDao.deleteEventById(itemId)
                            else -> { /* do nothing */ }
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
                    is EventItem -> eventDao.updateSyncStatusById(itemId, status.intValue)
                    else -> { /* unsupported */ }
                }
            }
        }.doOnComplete {
            synchronized(lock) {
                inMemoryBoard.items.firstOrNull { itemId == it.itemId }?.let {
                    it.syncStatus = status
                }
            }
        }
    }

    override fun getSyncTime(): Date = Date()

    private fun getEventsFromDeviceCalendars(): Flowable<List<DeviceEvent>> {
        return eventPref.getPreference(false)
            .map { pref ->
                val calendars = pref.calendarPreference.calendars.filter { it.isSyncedToBoard }
                for (calendar in calendars) {
                    AppLogger.d("Synced calendar: $calendar")
                }
                val addedCalendarIds = eventPref.getCalendarDiff().getAdded()
                val removedCalendarIds = eventPref.getCalendarDiff().getRemoved()
                for (calId in addedCalendarIds) {
                    AppLogger.d("Added calendar IDs: $calId")
                }
                for (calId in removedCalendarIds) {
                    AppLogger.d("Removed calendar ID: $calId")
                }
                eventPref.clearCalendarDiff()
                ArrayList<DeviceEvent>()
            }
    }
}
