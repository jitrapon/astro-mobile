package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.BoardDatabase
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

class EventItemLocalDataSource(database: BoardDatabase, private val userInteractor: UserInteractor): EventItemDataSource {

    private lateinit var inMemoryItem: EventItem

    private var inMemoryDatePolls: MutableList<EventDatePoll> = ArrayList()

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

    override fun getDatePolls(item: EventItem, refresh: Boolean): Flowable<List<EventDatePoll>> {
        return Flowable.just(inMemoryDatePolls)
    }

    override fun saveDatePolls(polls: List<EventDatePoll>): Flowable<List<EventDatePoll>> {
        inMemoryDatePolls = polls.toMutableList()
        return Flowable.just(inMemoryDatePolls)
    }

    override fun updateDatePollCount(item: EventItem, poll: EventDatePoll, upvote: Boolean): Completable {
        return Completable.fromCallable {
            inMemoryDatePolls.find { it.id == poll.id }?.let {
                if (upvote) {
                    userInteractor.getCurrentUserId()?.let(it.users::add)
                }
                else {
                    userInteractor.getCurrentUserId()?.let(it.users::remove)
                }
            }
            inMemoryDatePolls
        }
    }

    override fun addDatePoll(item: EventItem, startDate: Date, endDate: Date?): Flowable<EventDatePoll> {
        throw NotImplementedError()
    }

    override fun setDatePollStatus(item: EventItem, open: Boolean): Completable {
        return Completable.fromCallable {
            eventDao.updateDatePollStatus(item.itemId, open)
            item.itemInfo.datePollStatus = open
            inMemoryItem
        }
    }
}