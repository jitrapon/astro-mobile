package io.jitrapon.glom.board

import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Main entry to the board data and its items
 *
 * Created by Jitrapon
 */
interface BoardDataSource {

    fun getBoard(circleId: String, itemType: Int, refresh: Boolean = false): Flowable<Board>

    fun saveBoard(board: Board): Flowable<Board>

    fun createItem(item: BoardItem, remote: Boolean = false): Completable

    fun editItem(item: BoardItem): Completable

    fun deleteItem(itemId: String, remote: Boolean = false): Completable

    fun setItemSyncStatus(itemId: String, status: SyncStatus): Completable

    fun syncItemPreference(board: Board, itemType: Int): Flowable<Board>
}
