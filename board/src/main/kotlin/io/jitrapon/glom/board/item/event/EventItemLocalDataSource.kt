package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.BoardDatabase
import io.jitrapon.glom.board.item.event.plan.EventDatePoll
import io.jitrapon.glom.board.item.event.plan.EventPlacePoll
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

class EventItemLocalDataSource(database: BoardDatabase, private val userInteractor: UserInteractor): EventItemDataSource {

    private lateinit var inMemoryItem: EventItem

    private var inMemoryDatePolls: MutableList<EventDatePoll> = ArrayList()

    private var inMemoryPlacePolls: MutableList<EventPlacePoll> = ArrayList()

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
            }
        }.doOnComplete {
            userInteractor.getCurrentUserId()?.let {
                item.itemInfo.attendees.add(it)
            }
        }
    }

    override fun leaveEvent(item: EventItem): Completable {
        return Completable.fromCallable {
            userInteractor.getCurrentUserId()?.let { userId ->
                eventDao.deleteAttendee(EventAttendeeEntity(item.itemId, userId))
            }
        }.doOnComplete {
            userInteractor.getCurrentUserId()?.let { currentUserId ->
                item.itemInfo.attendees.removeAll {
                    it.equals(currentUserId, true)
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
        }
    }

    override fun addDatePoll(item: EventItem, startDate: Date, endDate: Date?): Flowable<EventDatePoll> {
        throw NotImplementedError()
    }

    override fun setDatePollStatus(item: EventItem, open: Boolean): Completable {
        return Completable.fromCallable {
            eventDao.updateDatePollStatus(item.itemId, open)
        }.doOnComplete {
            item.itemInfo.datePollStatus = open
        }
    }

    override fun setDate(item: EventItem, startDate: Date?, endDate: Date?): Completable {
        return Completable.fromCallable {
            eventDao.updateDateTime(item.itemId, startDate?.time, endDate?.time)
        }.doOnComplete {
            item.itemInfo.apply {
                startTime = startDate?.time
                endTime = endDate?.time
            }
        }
    }

    override fun getPlacePolls(item: EventItem, refresh: Boolean): Flowable<List<EventPlacePoll>> {
        return Flowable.just(inMemoryPlacePolls)
    }

    override fun savePlacePolls(polls: List<EventPlacePoll>): Flowable<List<EventPlacePoll>> {
        inMemoryPlacePolls = polls.toMutableList()
        return Flowable.just(inMemoryPlacePolls)
    }

    override fun updatePlacePollCount(item: EventItem, poll: EventPlacePoll, upvote: Boolean): Completable {
        return Completable.fromCallable {
            inMemoryPlacePolls.find { it.id == poll.id }?.let {
                if (upvote) {
                    userInteractor.getCurrentUserId()?.let(it.users::add)
                }
                else {
                    userInteractor.getCurrentUserId()?.let(it.users::remove)
                }
            }
        }
    }

    override fun addPlacePoll(item: EventItem, placeId: String?, googlePlaceId: String?): Flowable<EventPlacePoll> {
        throw NotImplementedError()
    }

    override fun setPlacePollStatus(item: EventItem, open: Boolean): Completable {
        return Completable.fromCallable {
            eventDao.updatePlacePollStatus(item.itemId, open)
        }.doOnComplete {
            item.itemInfo.placePollStatus = open
        }
    }

    override fun setPlace(item: EventItem, location: EventLocation?): Completable {
        return Completable.fromCallable {
            if (location == null) {
                eventDao.updatePlace(item.itemId)
            }
            else {
                eventDao.updatePlace(item.itemId, location.googlePlaceId, location.placeId, location.latitude,
                        location.longitude, location.name, location.description, location.address)
            }
        }.doOnComplete {
            item.itemInfo.apply {
                this.location = location
            }
        }
    }
}