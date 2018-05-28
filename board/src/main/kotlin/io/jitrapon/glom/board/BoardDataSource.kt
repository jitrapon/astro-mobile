package io.jitrapon.glom.board

import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Main entry to the board data
 *
 * Created by Jitrapon
 */
interface BoardDataSource {

    fun getBoard(circleId: String, refresh: Boolean = false): Flowable<Board>

    fun createItem(item: BoardItem, remote: Boolean = false): Completable

    fun editItem(item: BoardItem): Completable

    fun deleteItem(itemId: String): Completable
}
