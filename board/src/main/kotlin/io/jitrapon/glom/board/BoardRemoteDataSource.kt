package io.jitrapon.glom.board

import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.repository.RemoteDataSource
import io.jitrapon.glom.board.event.EventItem
import io.jitrapon.glom.board.event.deserialize
import io.jitrapon.glom.board.event.serializeInfo
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

class BoardRemoteDataSource(private val circleInteractor: CircleInteractor) : RemoteDataSource(), BoardDataSource {

    private val api = retrofit.create(BoardApi::class.java)

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        return api.getBoard(circleId, getTypeQuery(itemType)).map {
            it.deserialize()
        }
    }

    override fun createItem(item: BoardItem, remote: Boolean): Completable {
        return api.createBoardItem(circleInteractor.getActiveCircleId(), item.serialize())
    }

    override fun editItem(item: BoardItem): Completable {
        return api.editBoardItem(circleInteractor.getActiveCircleId(), item.itemId, item.serializeItemInfo())
    }

    override fun deleteItem(itemId: String, remote: Boolean): Completable {
        return api.deleteBoardItem(circleInteractor.getActiveCircleId(), itemId)
    }

    private fun getTypeQuery(type: Int): String? = when (type) {
        BoardItem.TYPE_EVENT -> "event"
        BoardItem.TYPE_DRAWING -> "drawing"
        BoardItem.TYPE_FILE -> "file"
        BoardItem.TYPE_LINK -> "link"
        BoardItem.TYPE_LIST -> "list"
        BoardItem.TYPE_NOTE -> "note"
        else -> null
    }

    //region deserializers

    private fun BoardResponse.deserialize(): Board {
        return Board(boardId = boardId, items = items.deserialize(), retrievedTime = Date())
    }

    private fun List<BoardItemResponse>.deserialize(): ArrayList<BoardItem> {
        return ArrayList<BoardItem>().apply {
            this@deserialize.forEach {
                when (it.itemType) {
                    BoardItem.TYPE_EVENT -> add(it.deserialize())
                }
            }
        }
    }

    //region serializers
    //endregion

    private fun BoardItem.serialize(): BoardItemRequest {
        val itemInfoRequest = when (itemType) {
            BoardItem.TYPE_EVENT -> (this as EventItem).serializeInfo()
            else -> throw Exception("Item type $itemType is unserializable")
        }
        return BoardItemRequest(itemId, itemType, createdTime, owners, itemInfoRequest)
    }

    private fun BoardItem.serializeItemInfo(): MutableMap<String, Any?> {
        return when (itemType) {
            BoardItem.TYPE_EVENT -> (this as EventItem).serializeInfo()
            else -> throw Exception("Item info for type $itemType is unserializable")
        }
    }

    //endregion
}
