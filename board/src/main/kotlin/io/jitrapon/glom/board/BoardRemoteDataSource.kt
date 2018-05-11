package io.jitrapon.glom.board

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.jitrapon.glom.board.event.EventInfo
import io.jitrapon.glom.board.event.EventItem
import io.reactivex.Completable
import io.reactivex.Flowable
import org.json.JSONArray
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

    private fun JSONArray.toIntList(): MutableList<Int> {
        return Collections.emptyList()
    }

    private fun JSONArray.toStringList(): MutableList<String> {
        return Collections.emptyList()
    }

    private fun BoardResponse.deserialize(): Board {
        return Board(boardId = boardId, items = items.deserialize(), retrievedTime = Date())
    }

    private fun List<BoardItemResponse>.deserialize(): ArrayList<BoardItem> {
        val now = Date()
        return ArrayList<BoardItem>().apply {
            this@deserialize.forEach {
                when (it.itemType) {
                    BoardItem.TYPE_EVENT -> add(EventItem(BoardItem.TYPE_EVENT, it.itemId, it.createdTime,
                            it.updatedTime, it.owners,
                            EventInfo("play game", null, null, null, null, "Asia/Bangkok", false,
                                    null, false, false, arrayListOf("yoshi3003")),
                            now)
                    )
                }
            }
        }
    }

    //endregion
}
