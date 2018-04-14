package io.jitrapon.glom.board.event

import io.reactivex.Completable

/**
 * Main entry point to event items
 *
 * Created by Jitrapon
 */
interface EventItemDataSource {

    fun setItem(item: EventItem)

    fun getItem(): EventItem

    fun saveItem(info: EventInfo): Completable

    fun joinEvent(userId: String, item: EventItem): Completable

    fun leaveEvent(userId: String, item: EventItem): Completable
}
