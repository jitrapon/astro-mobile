package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.event.plan.EventDatePoll
import io.jitrapon.glom.board.item.event.plan.EventPlacePoll
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

    override fun setName(name: String?) {
        localDataSource.setName(name)
    }

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

    override fun setDatePollStatus(item: EventItem, open: Boolean): Completable {
        return update(
                localDataSource.setDatePollStatus(item, open),
                remoteDataSource.setDatePollStatus(item, open),
                false)
    }

    override fun setDateRemote(item: EventItem, startDate: Date?, endDate: Date?): Completable {
        return update(
                localDataSource.setDateRemote(item, startDate, endDate),
                remoteDataSource.setDateRemote(item, startDate, endDate),
                false)
    }

    override fun setDate(startDateMs: Long?, endDateMs: Long?) {
        localDataSource.setDate(startDateMs, endDateMs)
    }

    override fun getPlacePolls(item: EventItem, refresh: Boolean): Flowable<List<EventPlacePoll>> {
        return loadTypedList(refresh,
                localDataSource.getPlacePolls(item),
                remoteDataSource.getPlacePolls(item),
                localDataSource::savePlacePolls)
    }

    override fun savePlacePolls(polls: List<EventPlacePoll>): Flowable<List<EventPlacePoll>> {
        throw NotImplementedError()
    }

    override fun updatePlacePollCount(item: EventItem, poll: EventPlacePoll, upvote: Boolean): Completable {
        return update(
                localDataSource.updatePlacePollCount(item, poll, upvote),
                remoteDataSource.updatePlacePollCount(item, poll, upvote),
                false
        )
    }

    override fun addPlacePoll(item: EventItem, placeId: String?, googlePlaceId: String?): Flowable<EventPlacePoll> {
        return remoteDataSource.addPlacePoll(item, placeId, googlePlaceId)
    }

    override fun setPlacePollStatus(item: EventItem, open: Boolean): Completable {
        return update(
                localDataSource.setPlacePollStatus(item, open),
                remoteDataSource.setPlacePollStatus(item, open),
                false)
    }

    override fun setPlace(item: EventItem, location: EventLocation?): Completable {
        return update(
                localDataSource.setPlace(item, location),
                remoteDataSource.setPlace(item, location),
                false)
    }

    override fun setLocation(location: EventLocation?) {
        localDataSource.setLocation(location)
    }

    override fun setNote(note: String?) {
        localDataSource.setNote(note)
    }
}
