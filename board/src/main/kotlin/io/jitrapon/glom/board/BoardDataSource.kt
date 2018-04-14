package io.jitrapon.glom.board

import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Main entry to the board data
 *
 * Created by Jitrapon
 */
interface BoardDataSource {

    fun getBoard(circleId: String): Flowable<Board>

    fun addItem(item: BoardItem): Completable

    fun editItem(item: BoardItem): Completable

    fun deleteItem(itemId: String): Completable

    fun createItem(item: BoardItem): Completable
}
