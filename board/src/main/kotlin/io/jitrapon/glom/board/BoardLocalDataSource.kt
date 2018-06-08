package io.jitrapon.glom.board

import io.reactivex.Completable
import io.reactivex.Flowable

class BoardLocalDataSource : BoardDataSource {

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        TODO()
    }

    override fun createItem(item: BoardItem, remote: Boolean): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun editItem(item: BoardItem): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteItem(itemId: String, remote: Boolean): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
