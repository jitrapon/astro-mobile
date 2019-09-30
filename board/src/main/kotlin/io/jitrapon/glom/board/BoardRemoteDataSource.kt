package io.jitrapon.glom.board

import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.model.ContentChangeInfo
import io.jitrapon.glom.base.repository.RemoteDataSource
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.BoardItemRequest
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.deserialize
import io.jitrapon.glom.board.item.event.serializeInfo
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import java.util.ArrayList
import java.util.Date

class BoardRemoteDataSource(
    private val circleInteractor: CircleInteractor,
    override val contentChangeNotifier: PublishSubject<ContentChangeInfo>
) : RemoteDataSource(),
    BoardDataSource {

    private val api = retrofit.create(BoardApi::class.java)

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        return api.getBoard(circleId, itemType.toString()).map {
            it.deserialize(circleId)
        }
    }

    override fun saveBoard(board: Board): Flowable<Board> {
        throw NotImplementedError()
    }

    override fun createItem(circleId: String, item: BoardItem, remote: Boolean): Completable {
        return api.createBoardItem(circleInteractor.getActiveCircleId(), item.serialize())
    }

    override fun editItem(circleId: String, item: BoardItem, remote: Boolean): Completable {
        return api.editBoardItem(
            circleInteractor.getActiveCircleId(),
            item.itemId,
            item.serializeItemInfo()
        )
    }

    override fun deleteItem(circleId: String, itemId: String, remote: Boolean): Completable {
        return api.deleteBoardItem(circleInteractor.getActiveCircleId(), itemId)
    }

    override fun setItemSyncStatus(itemId: String, status: SyncStatus): Completable {
        throw NotImplementedError()
    }

    //region deserializers

    private fun BoardResponse.deserialize(circleId: String): Board {
        return Board(circleId = circleId, items = items.deserialize(), retrievedTime = Date())
    }

    private fun List<BoardItemResponse>.deserialize(): ArrayList<BoardItem> {
        return ArrayList<BoardItem>().apply {
            this@deserialize.forEach {
                when (it.itemType) {
                    BoardItem.TYPE_EVENT -> add(it.deserialize(circleInteractor.getActiveCircleId()))
                }
            }
        }
    }

    override fun getSyncTime(): Date = Date()

    override fun invalidateCache() {
        throw NotImplementedError()
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

    override fun cleanUpContentChangeNotifier() = Unit
}
