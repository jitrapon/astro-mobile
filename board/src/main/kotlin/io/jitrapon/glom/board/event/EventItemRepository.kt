package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.BoardItem
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

/**
 * Retrieves, stores, and saves the state of a board item
 *
 * Created by Jitrapon
 */
class EventItemRepository(private val remoteDataSource: EventItemDataSource, private val localDataSource: EventItemLocalDataSource) :
        Repository<BoardItem>(), EventItemDataSource {

    override fun initWith(item: EventItem) {
        localDataSource.initWith(item)
    }

    override fun getItem(): Flowable<EventItem> = localDataSource.getItem()

    override fun saveItem(info: EventInfo): Completable = localDataSource.saveItem(info)

    override fun joinEvent(item: EventItem): Completable {
        return update(
                localDataSource.joinEvent(item),
                remoteDataSource.joinEvent(item),
                false
        )
    }

    override fun leaveEvent(item: EventItem): Completable {
        return update(
                localDataSource.leaveEvent(item),
                remoteDataSource.leaveEvent(item),
                false
        )
    }

    override fun getDatePolls(item: EventItem, refresh: Boolean): Flowable<List<EventDatePoll>> {
        return loadTypedList(refresh,
                localDataSource.getDatePolls(item),
                remoteDataSource.getDatePolls(item),
                localDataSource::saveDatePolls)
    }

    override fun saveDatePolls(polls: List<EventDatePoll>): Flowable<List<EventDatePoll>> {
        throw NotImplementedError()
    }

    override fun updateDatePollCount(item: EventItem, poll: EventDatePoll, upvote: Boolean): Completable {
        return update(
                localDataSource.updateDatePollCount(item, poll, upvote),
                remoteDataSource.updateDatePollCount(item, poll, upvote),
                false)
    }

    override fun addDatePoll(item: EventItem, startDate: Date, endDate: Date?): Flowable<EventDatePoll> {
        return remoteDataSource.addDatePoll(item, startDate, endDate)
    }

    override fun setDatePollStatus(open: Boolean): Completable {
        return remoteDataSource.setDatePollStatus(open)
    }
}
