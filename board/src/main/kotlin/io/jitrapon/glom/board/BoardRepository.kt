package io.jitrapon.glom.board

import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.event.*
import io.reactivex.Flowable
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository for retrieving and saving Board information
 *
 * @author Jitrapon Tiachunpun
 */
object BoardRepository : Repository<Board>() {

    /*
     * Cached board state. Will be updated whenever loadBoard() function
     * is called
     */
    private var board: Board? = null

    override fun load(): Flowable<Board> {
        board = board ?: Board("abcd1234", getItems())
        return Flowable.just(board!!).delay(1000L, TimeUnit.MILLISECONDS)
    }

    fun getCache(): Board? = board

    override fun loadList(): Flowable<List<Board>> {
        //not applicable
        throw NotImplementedError()
    }

    private fun getItems() = ArrayList<BoardItem>().apply {
        val houseLocation = EventLocation(13.732756, 100.643237, null, null)
        val condoLocation = EventLocation(13.722591, 100.580225, null, null)
        val dinnerLocation = EventLocation(null, null, "ChIJhfF-wgOf4jARwAQMPbMAAQ8", null)
        val shoppingLocation = EventLocation(null, null, "ChIJcWLOjAGf4jARWrAvcZ1YFDM", null)
        val cafeLocation = EventLocation(null, null, "ChIJEyfe5-ee4jAR8d8sxPe29PA", null)

        val createdTime = Date().time
        val repeatEvery3Days = RepeatInfo(19, false, 0, 3, 0L, null)
        val repeatEveryWeek = RepeatInfo(1, false, 1, 1, 0L, listOf(1, 3, 5))

        add(EventItem(BoardItem.TYPE_EVENT, "1", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("House Party!", 1512993600000L, null, houseLocation, "Celebrate my birthday", "Asia/Bangkok",
                        false, null, false, false, arrayListOf("yoshi3003", "fatcat18", "fluffy", "panda"))))

        add(EventItem(BoardItem.TYPE_EVENT, "2", createdTime, createdTime, listOf("yoshi3003", "fatcat18"),
                EventInfo("Dinner", 1513076400000L, 1508594400000L, dinnerLocation, null, "Asia/Bangkok",
                        false, repeatEvery3Days, false, false, arrayListOf("fatcat18"))))

        add(EventItem(BoardItem.TYPE_EVENT, "3", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("gym", 1512720000000L, null, null, null, "Asia/Bangkok",
                        false, repeatEveryWeek, false, false, arrayListOf("yoshi3003"))))

        add(EventItem(BoardItem.TYPE_EVENT, "4", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Shopping", null, null, shoppingLocation, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("fatcat18"))))

        add(EventItem(BoardItem.TYPE_EVENT, "5", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Board game night", null, null, cafeLocation, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("yoshi3003", "fluffy", "panda"))))

        add(EventItem(BoardItem.TYPE_EVENT, "6", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Beach trip", 1513702800000L, 1514048400000L, null, null, "Asia/Bangkok",
                        true, null, false, false, arrayListOf("fatcat18"))))

        add(EventItem(BoardItem.TYPE_EVENT, "7", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Christmas Party", 1514203200000L, null, houseLocation, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("fluffy"))))

        add(EventItem(BoardItem.TYPE_EVENT, "8", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Office christmas dinner", 1513310400000L, null, null, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("panda"))))

        add(EventItem(BoardItem.TYPE_EVENT, "9", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Movie - Star Wars: The Last Jedi", 1513429200000L, 1513438200000L, null, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("yoshi3003"))))

        add(EventItem(BoardItem.TYPE_EVENT, "10", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("2018 new year party", 1514725200000L, 1514746800000L, condoLocation, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("yoshi3003"))))
    }

    fun joinEvent(userId: String, itemId: String?): Flowable<EditAttendeeResponse> {
        return Flowable.just(EditAttendeeResponse(userId, 2))
                .flatMap { response ->
                    Flowable.fromCallable {
                        board?.items?.find { it.itemId == itemId }?.let {
                            if (it is EventItem) {
                                it.itemInfo.attendees.add(userId)
                                response.attendees = it.itemInfo.attendees
                            }
                        }
                        response
                    }
                }
                .delay(1000L, TimeUnit.MILLISECONDS)
    }

    fun declineEvent(userId: String, itemId: String?): Flowable<EditAttendeeResponse> {
        return Flowable.just(EditAttendeeResponse(userId, 1))
                .flatMap { response ->
                    Flowable.fromCallable {
                        board?.items?.find { it.itemId == itemId }?.let {
                            if (it is EventItem) {
                                it.itemInfo.attendees.removeAll {
                                    it.equals(userId, true)
                                }
                                response.attendees = it.itemInfo.attendees
                            }
                        }
                        response
                    }
                }
                .delay(1000L, TimeUnit.MILLISECONDS)
    }

    /**
     * Edits the board item's specific item info using the specified new board item
     */
    fun editBoardItemInfo(item: BoardItem): Flowable<BoardItem> {
        return Flowable.just(EditBoardItemResponse("circleId", "123"))
                .flatMap { response ->
                    Flowable.fromCallable {
                        board?.items?.let {
                            val index = it.indexOfFirst { it.itemId == item.itemId }
                            if (index != -1) it[index] = item
                        }
                        item
                    }
                }
                .delay(2000L, TimeUnit.MILLISECONDS)
    }
}