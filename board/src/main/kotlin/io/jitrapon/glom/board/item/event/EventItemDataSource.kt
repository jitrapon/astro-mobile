package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.board.item.event.plan.EventDatePoll
import io.jitrapon.glom.board.item.event.plan.EventPlacePoll
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

/**
 * Main entry point to event items in board
 *
 * Created by Jitrapon
 */
interface EventItemDataSource {

    fun initWith(item: EventItem)

    fun getItem(): Flowable<EventItem>

    fun saveItem(info: EventInfo): Completable

    fun setName(name: String?)

    fun joinEvent(item: EventItem): Completable

    fun leaveEvent(item: EventItem): Completable

    fun getDatePolls(item: EventItem, refresh: Boolean = true): Flowable<List<EventDatePoll>>

    fun updateDatePollCount(item: EventItem, poll: EventDatePoll, upvote: Boolean): Completable

    fun addDatePoll(item: EventItem, startDate: Date, endDate: Date?): Flowable<EventDatePoll>

    fun saveDatePolls(polls: List<EventDatePoll>): Flowable<List<EventDatePoll>>

    fun setDatePollStatus(item: EventItem, open: Boolean): Completable

    fun setDateRemote(item: EventItem, startDate: Date?, endDate: Date?): Completable

    fun setDate(startDateMs: Long?, endDateMs: Long?)

    fun getPlacePolls(item: EventItem, refresh: Boolean = true): Flowable<List<EventPlacePoll>>

    fun updatePlacePollCount(item: EventItem, poll: EventPlacePoll, upvote: Boolean): Completable

    fun addPlacePoll(item: EventItem, placeId: String?, googlePlaceId: String?): Flowable<EventPlacePoll>

    fun savePlacePolls(polls: List<EventPlacePoll>): Flowable<List<EventPlacePoll>>

    fun setPlacePollStatus(item: EventItem, open: Boolean): Completable

    fun setPlace(item: EventItem, location: EventLocation?): Completable

    fun setLocation(location: EventLocation?)
}
