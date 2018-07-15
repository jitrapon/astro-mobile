package io.jitrapon.glom.board.event

import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Main entry point to event items in board
 *
 * Created by Jitrapon
 */
interface EventItemDataSource {

    fun initWith(item: EventItem)

    fun getItem(): Flowable<EventItem>

    fun saveItem(info: EventInfo): Completable

    fun joinEvent(item: EventItem): Completable

    fun leaveEvent(item: EventItem): Completable

    fun getDatePolls(item: EventItem, refresh: Boolean = true): Flowable<List<EventDatePoll>>

    fun updateDatePollCount(item: EventItem, poll: EventDatePoll, upvote: Boolean): Completable

    fun addDatePoll(item: EventItem): Flowable<EventDatePoll>

    fun saveDatePolls(polls: List<EventDatePoll>): Flowable<List<EventDatePoll>>
}
