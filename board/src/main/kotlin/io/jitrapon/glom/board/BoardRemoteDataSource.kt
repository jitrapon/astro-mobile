package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.base.repository.RemoteDataSource
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.board.event.EventInfo
import io.jitrapon.glom.board.event.EventItem
import io.jitrapon.glom.board.event.EventLocation
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

class BoardRemoteDataSource : RemoteDataSource(), BoardDataSource {

    private val api = retrofit.create(BoardApi::class.java)

    override fun getBoard(circleId: String, refresh: Boolean): Flowable<Board> {
        return api.getBoard().map {
            it.deserialize()
        }
    }

    override fun addItem(item: BoardItem): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun editItem(item: BoardItem): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteItem(itemId: String): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createItem(item: BoardItem): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    //region deserializers

    private fun BoardResponse.deserialize(): Board {
        return Board(boardId = boardId, items = items.deserialize(), retrievedTime = Date())
    }

    private fun List<BoardItemResponse>.deserialize(): ArrayList<BoardItem> {
        val now = Date()
        return ArrayList<BoardItem>().apply {
            this@deserialize.forEach {
                when (it.itemType) {
                    BoardItem.TYPE_EVENT -> {
                        add(EventItem(BoardItem.TYPE_EVENT, it.itemId, it.createdTime, it.updatedTime, it.owners,
                                it.info.let {
                                    EventInfo(it["event_name"] as String,
                                            it["start_time"].asNullableLong(),
                                            it["end_time"].asNullableLong(),
                                            (it["location"] as? Map<*, *>)?.let {
                                                EventLocation(it["lat"].asNullableDouble(),
                                                        it["long"].asNullableDouble(),
                                                        it["g_place_id"] as? String?,
                                                        it["place_id"] as? String?
                                                )
                                            },
                                            it["note"] as? String?,
                                            it["time_zone"] as? String?,
                                            it["is_full_day"] as Boolean,
                                            (it["repeat"] as? Map<*, *>)?.let {
                                                RepeatInfo(it["occurence_id"].asNullableInt(),
                                                        it["is_reschedule"] as? Boolean?,
                                                        it["unit"].asInt(),
                                                        it["interval"].asLong(),
                                                        it["until"].asLong(),
                                                        it["meta"].asNullableIntList()
                                                )
                                            },
                                            it["is_date_poll_opened"] as Boolean,
                                            it["is_date_poll_opened"] as Boolean,
                                            ArrayList(it["attendees"] as List<String>))
                                }, now)
                        )
                    }
                }
            }
            AppLogger.i("Added item")
        }
    }

    //endregion
}
