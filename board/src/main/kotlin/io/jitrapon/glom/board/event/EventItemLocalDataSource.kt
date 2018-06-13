package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.BoardDatabase
import io.reactivex.Completable
import io.reactivex.Flowable

class EventItemLocalDataSource(database: BoardDatabase, private val userInteractor: UserInteractor): EventItemDataSource {

    private lateinit var inMemoryItem: EventItem

    /* DAO access object to event items */
    private val eventDao: EventItemDao = database.eventItemDao()

    override fun initWith(item: EventItem) {
        inMemoryItem = item
    }

    override fun getItem(): Flowable<EventItem> = Flowable.just(inMemoryItem)

    override fun saveItem(info: EventInfo): Completable {
        return Completable.fromCallable {
            inMemoryItem.setInfo(info)
        }
    }

    override fun joinEvent(item: EventItem): Completable {
        return Completable.fromCallable {
            userInteractor.getCurrentUserId()?.let {
                eventDao.insertAttendee(EventAttendeeEntity(item.itemId, it))
                item.itemInfo.attendees.add(it)
            }
        }
    }

    override fun leaveEvent(item: EventItem): Completable {
        return Completable.fromCallable {
            userInteractor.getCurrentUserId()?.let { userId ->
                eventDao.deleteAttendee(EventAttendeeEntity(item.itemId, userId))
                item.itemInfo.attendees.removeAll {
                    it.equals(userId, true)
                }
            }
        }
    }
}