package io.jitrapon.glom.board

import android.os.Parcel
import io.jitrapon.glom.base.data.AsyncErrorResult
import io.jitrapon.glom.base.data.AsyncResult
import io.jitrapon.glom.base.data.AsyncSuccessResult
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

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
     * Cached board state. Will be updated whenever loadBoard() function
     * is called
     */
    private var board: Board? = null

    init {
        networkRepository = InMemoryBoardRepository()
    }

    /**
     * Force reload of the board state. Will default to network repository. If network fails,
     * resort to local repository to retrieve board.
     */
    fun loadBoard(onComplete: (AsyncResult<Board>) -> Unit) {
        networkRepository.load()
                .delay(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    board = it
                    board?.let { onComplete(AsyncSuccessResult(it)) }
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing here
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