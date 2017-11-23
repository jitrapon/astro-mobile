package io.jitrapon.glom.board

import android.os.Parcel
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.jitrapon.glom.board.data.Board
import io.jitrapon.glom.board.data.BoardItem
import io.jitrapon.glom.board.data.InMemoryBoardRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Interactor for dealing with Board logic
 *
 * @author Jitrapon Tiachunpun
 */
class BoardInteractor {

    /*
     * Repository that requests from network to retrieve data
     */
    private lateinit var networkRepository: InMemoryBoardRepository

    /*
     * Cached board items
     */
    private var items: List<BoardItem>? = null

    init {
        networkRepository = InMemoryBoardRepository()
    }

    fun loadBoardItems(onComplete: (AsyncResult<List<BoardItem>>) -> Unit) {
        networkRepository.load()
                .map { it.items }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    items = it
                    items?.let { onComplete(AsyncSuccessResult(it)) }
                }, {
                    onComplete(AsyncErrorResult(it))
                })
    }

    private fun testParcelable(board: Board) {
        val parcel = Parcel.obtain()
        board.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val parceledBoard = Board.CREATOR.createFromParcel(parcel)
        for (i in board.items.indices) {
            val item = board.items[i]
            val parceledItem = parceledBoard.items[i]
            if (item == parceledItem) println("equal") else println("unequal")
        }
        parcel.recycle()
    }
}