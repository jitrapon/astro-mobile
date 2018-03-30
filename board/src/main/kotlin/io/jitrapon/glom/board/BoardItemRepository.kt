package io.jitrapon.glom.board

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.event.EditAttendeeResponse
import io.jitrapon.glom.board.event.EventInfo
import io.reactivex.Flowable
import java.util.concurrent.TimeUnit

/**
 * Retrieves, stores, and saves the state of a board item
 *
 * Created by Jitrapon
 */
object BoardItemRepository : Repository<BoardItem>() {

    private var item: BoardItem? = null

    override fun load(): Flowable<BoardItem> {
        throw NotImplementedError()
    }

    override fun loadList(): Flowable<List<BoardItem>> {
        throw NotImplementedError()
    }

    fun setCache(boardItem: BoardItem) {
        item = boardItem
    }

    fun getCache(): BoardItem? = item

    fun save(info: BoardItemInfo) {
        item?.setInfo(info)
    }

    fun joinEvent(userId: String): Flowable<EditAttendeeResponse> {
        return Flowable.just(EditAttendeeResponse(userId, 2))
                .flatMap { response ->
                    Flowable.fromCallable {
                        (item?.itemInfo as? EventInfo)?.let {
                            it.attendees.add(userId)
                            response.attendees = it.attendees
                        }
                        response
                    }
                }
                .delay(1000L, TimeUnit.MILLISECONDS)
    }

    fun declineEvent(userId: String): Flowable<EditAttendeeResponse> {
        return Flowable.just(EditAttendeeResponse(userId, 1))
                .flatMap { response ->
                    Flowable.fromCallable {
                        (item?.itemInfo as? EventInfo)?.let {
                            it.attendees.removeAll {
                                it.equals(userId, true)
                            }
                            response.attendees = it.attendees
                        }
                        response
                    }
                }
                .delay(1000L, TimeUnit.MILLISECONDS)
    }
}
