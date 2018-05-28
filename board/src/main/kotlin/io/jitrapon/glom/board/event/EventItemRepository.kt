package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.BoardItem
import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Retrieves, stores, and saves the state of a board item
 *
 * Created by Jitrapon
 */
class EventItemRepository(private val remoteDataSource: EventItemDataSource) : Repository<BoardItem>(), EventItemDataSource {

    private lateinit var item: EventItem

    override fun initWith(item: EventItem) {
        this.item = item
    }

    override fun getItem(): Flowable<EventItem> = Flowable.just(item)

    override fun saveItem(info: EventInfo): Completable {
        return Completable.fromCallable {
            item.setInfo(info)
        }
    }

    override fun joinEvent(item: EventItem): Completable {
        return update(
                Completable.fromCallable {
                    item.itemInfo.attendees.add("yoshi3003")
                },
                remoteDataSource.joinEvent(item),
                false
        )
    }

    override fun leaveEvent(item: EventItem): Completable {
        return update(
                Completable.fromCallable {
                    item.itemInfo.attendees.removeAll {
                        it.equals("yoshi3003", true)
                    }
                },
                remoteDataSource.leaveEvent(item),
                false
        )
    }
}
