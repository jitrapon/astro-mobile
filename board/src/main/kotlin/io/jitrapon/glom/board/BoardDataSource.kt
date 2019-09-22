package io.jitrapon.glom.board

import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import java.util.Date

/**
 * Main entry to the board data and its items
 *
 * Created by Jitrapon
 */
interface BoardDataSource {

    /* A subject that emits true if the underlying data retrieved has changed to the observer(s) */
    val contentChangeNotifier: PublishSubject<Boolean>

    fun cleanUpContentChangeNotifier()

    fun getBoard(circleId: String, itemType: Int, refresh: Boolean = false): Flowable<Board>

    fun saveBoard(board: Board): Flowable<Board>

    fun createItem(item: BoardItem, remote: Boolean = false): Completable

    fun editItem(item: BoardItem, remote: Boolean = false): Completable

    fun deleteItem(itemId: String, remote: Boolean = false): Completable

    fun setItemSyncStatus(itemId: String, status: SyncStatus): Completable

    fun getSyncTime(): Date
}
