package io.jitrapon.glom.board

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.reactivex.Completable
import io.reactivex.Flowable

/**
 * Repository for retrieving and saving Board information
 *
 * @author Jitrapon Tiachunpun
 */
class BoardRepository(private val remoteDataSource: BoardDataSource, private val localDataSource: BoardDataSource) :
        Repository<Board>(), BoardDataSource {

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        return load(refresh,
                localDataSource.getBoard(circleId, itemType),
                remoteDataSource.getBoard(circleId, itemType),
                localDataSource::saveBoard
        )
    }

    override fun saveBoard(board: Board): Flowable<Board> {
        throw NotImplementedError()
    }

    override fun createItem(item: BoardItem, remote: Boolean): Completable {
        return if (remote) remoteDataSource.createItem(item)
        else localDataSource.createItem(item, remote)
    }

    override fun editItem(item: BoardItem): Completable {
        return update(localDataSource.editItem(item), remoteDataSource.editItem(item), true)
    }

    override fun deleteItem(itemId: String, remote: Boolean): Completable {
        return if (remote) remoteDataSource.deleteItem(itemId)
        else localDataSource.deleteItem(itemId, remote)
    }

    override fun setItemSyncStatus(itemId: String, status: SyncStatus): Completable {
        return localDataSource.setItemSyncStatus(itemId, status)
    }

    override fun syncItemPreference(board: Board, itemType: Int): Flowable<Board> {
        return localDataSource.syncItemPreference(board, itemType)
    }
}
