package io.jitrapon.glom.board

import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.event.*
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class BoardLocalDataSource(database: BoardDatabase, private val calendarDao: CalendarDao, private val userInteractor: UserInteractor) : BoardDataSource {

    /* synchronized lock for modifying in-memory board */
    private val lock: Any = Any()
    private lateinit var inMemoryBoard: Board

    /* previously fetched board item type */
    private var lastFetchedItemType: AtomicInteger = AtomicInteger(Integer.MIN_VALUE)

    /* DAO access object to event items in local DB */
    private val eventDao: EventItemDao = database.eventItemDao()

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        // if this item type request has been requested before, just return the cached board version
        return if (lastFetchedItemType.get() == itemType) Flowable.just(inMemoryBoard)

        // otherwise invoke the corresponding item DAO
        else {
            when (itemType) {
                BoardItem.TYPE_EVENT -> {
                    eventDao.getEventsInCircle(circleId).toFlowable()
                            .map {
                                calendarDao.getEvents().blockingFirst()
                                it.toBoard(circleId, userInteractor.getCurrentUserId())
                            }
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
            //TODO delete unsynced items?
            val eventEntities = ArrayList<EventItemFullEntity>()
            val updatedTime = Date().time
            board.items.forEach {
                when (it) {
                    is EventItem -> eventEntities.add(it.toEntity(board.circleId, userInteractor.getCurrentUserId(), updatedTime))
                    else -> { /* do nothing */ }
                }
            }
            if (eventEntities.isNotEmpty()) eventDao.insertOrReplaceEvents(*eventEntities.toTypedArray())

            // find items that should be deleted
            val itemsInDB = eventDao.getEventsInCircle(board.circleId).blockingGet()
            deleteItemsNotFoundInNewBoard(eventEntities, itemsInDB.toMutableList())
        }.flatMap {
            eventDao.getEventsInCircle(board.circleId)
                    .map { it.toBoard(board.circleId, userInteractor.getCurrentUserId()) }
                    .toFlowable()
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
                if (newItemsIdSet.contains(it.entity.id)) iter.remove()
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
}
