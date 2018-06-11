package io.jitrapon.glom.board

import io.jitrapon.glom.board.event.*
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class BoardLocalDataSource(database: BoardDatabase) : BoardDataSource {

    private val eventDao: EventItemDao = database.eventItemDao()

    private lateinit var board: Board
    private var lastFetchedItemType: AtomicInteger = AtomicInteger(Integer.MIN_VALUE)

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        return when (itemType) {
            BoardItem.TYPE_EVENT -> {
                if (lastFetchedItemType.get() == itemType) {
                    Flowable.just(board)
                }
                else {
                    eventDao.getEventsInCircle(circleId).toFlowable().map {
                        board = it.toBoard(circleId)
                        lastFetchedItemType.set(itemType)
                        board
                    }
                }
            }
            else -> throw NotImplementedError()
        }
    }

    override fun saveBoard(board: Board): Flowable<Board> {
        return Flowable.fromCallable {
            val entities = ArrayList<EventItemFullEntity>()
            val updatedTime = Date().time
            board.items.forEach {
                when (it) {
                    is EventItem -> {
                        entities.add(EventItemFullEntity().apply {
                            entity = EventItemEntity(
                                    it.itemId, updatedTime, it.itemInfo.eventName, it.itemInfo.startTime, it.itemInfo.endTime, it.itemInfo.location?.googlePlaceId,
                                    it.itemInfo.location?.placeId, it.itemInfo.location?.latitude, it.itemInfo.location?.longitude,
                                    it.itemInfo.location?.name, it.itemInfo.note, it.itemInfo.timeZone, it.itemInfo.isFullDay, it.itemInfo.datePollStatus,
                                    it.itemInfo.placePollStatus, board.circleId)
                            attendees = it.itemInfo.attendees
                        })
                    }
                    else -> throw NotImplementedError()
                }
            }
            eventDao.insertOrReplaceEvents(entities)
            this.board = board
            board
        }
    }

    override fun createItem(item: BoardItem, remote: Boolean): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun editItem(item: BoardItem): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteItem(itemId: String, remote: Boolean): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
