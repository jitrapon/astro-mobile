package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.BoardItem
import io.reactivex.Completable
import java.util.concurrent.TimeUnit

/**
 * Retrieves, stores, and saves the state of a board item
 *
 * Created by Jitrapon
 */
class EventItemRepository : Repository<BoardItem>(), EventItemDataSource {

    private lateinit var item: EventItem

    override fun setItem(item: EventItem) {
        this.item = item
    }

    override fun getItem(): EventItem = item

    override fun saveItem(info: EventInfo): Completable {
        return Completable.fromCallable {
            item.setInfo(info)
        }
    }

    override fun joinEvent(userId: String, item: EventItem): Completable {
        return Completable.fromCallable {
            item.itemInfo.attendees.add(userId)
        }.delay(1000L, TimeUnit.MILLISECONDS)
    }

    override fun leaveEvent(userId: String, item: EventItem): Completable {
        return Completable.fromCallable {
            item.itemInfo.attendees.removeAll {
                it.equals(userId, true)
            }
        }.delay(1000L, TimeUnit.MILLISECONDS)
    }
}
