package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.ContentChangeInfo
import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import java.util.Date

/**
 * Repository for retrieving and saving Board information
 *
 * @author Jitrapon Tiachunpun
 */
class BoardRepository(
    private val remoteDataSource: BoardDataSource,
    private val localDataSource: BoardDataSource,
    override val contentChangeNotifier: PublishSubject<ContentChangeInfo>
) :
    Repository<Board>(), BoardDataSource {

    override fun getBoard(circleId: String, itemType: Int, refresh: Boolean): Flowable<Board> {
        return load(
            refresh,
            localDataSource.getBoard(circleId, itemType),
            remoteDataSource.getBoard(circleId, itemType),
            localDataSource::saveBoard
        )
    }

    override fun saveBoard(board: Board): Flowable<Board> {
        throw NotImplementedError()
    }

    override fun createItem(circleId: String, item: BoardItem, remote: Boolean): Completable {
        return if (remote && item.isSyncable) remoteDataSource.createItem(circleId, item)
        else localDataSource.createItem(circleId, item, remote)
    }

    override fun editItem(circleId: String, item: BoardItem, remote: Boolean): Completable {
        return if (remote && item.isSyncable) update(
            localDataSource.editItem(circleId, item),
            remoteDataSource.editItem(circleId, item),
            true
        )
        else localDataSource.editItem(circleId, item)
    }

    override fun deleteItem(circleId: String, itemId: String, remote: Boolean): Completable {
        return if (remote) remoteDataSource.deleteItem(circleId, itemId)
        else localDataSource.deleteItem(circleId, itemId, remote)
    }

    override fun setItemSyncStatus(itemId: String, status: SyncStatus): Completable {
        return localDataSource.setItemSyncStatus(itemId, status)
    }

    override fun getSyncTime(): Date = Date()

    override fun cleanUpContentChangeNotifier() {
        contentChangeNotifier.onComplete()
        remoteDataSource.cleanUpContentChangeNotifier()
        localDataSource.cleanUpContentChangeNotifier()
    }
}
