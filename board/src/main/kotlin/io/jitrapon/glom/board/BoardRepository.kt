package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.base.repository.Repository
import io.jitrapon.glom.board.event.EventInfo
import io.jitrapon.glom.board.event.EventItem
import io.jitrapon.glom.board.event.EventLocation
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository for retrieving and saving Board information
 *
 * @author Jitrapon Tiachunpun
 */
class BoardRepository : Repository<Board>(), BoardDataSource {

    private var board: Board? = null

    override fun getBoard(circleId: String): Flowable<Board> {
        return if (board == null) {
            board = Board(circleId, getItems())
            Flowable.just(board!!).delay(1500L, TimeUnit.MILLISECONDS)
        }
        else {
            Flowable.just(board!!)
        }
    }

    override fun addItem(item: BoardItem): Completable {
        return Completable.fromCallable {
            board?.items?.add(item)
        }
    }

    override fun editItem(item: BoardItem): Completable {
        return Completable.fromCallable {
            board?.items?.let {
                val index = it.indexOfFirst { it.itemId == item.itemId }
                if (index != -1) it[index] = item
            }
        }.delay(5000L, TimeUnit.MILLISECONDS)
    }

    override fun deleteItem(itemId: String): Completable {
        return Completable.fromCallable {
            board?.items?.let {
                val index = it.indexOfFirst { it.itemId == itemId }
                it.removeAt(index)
            }
        }
    }

    override fun createItem(item: BoardItem): Completable {
        return Completable.fromCallable {

        }.delay(5000L, TimeUnit.MILLISECONDS)
    }

    override fun getData(): Flowable<Int> {
        return load(
                Flowable.just(1).delay(300L, TimeUnit.MILLISECONDS),
                Flowable.just(2).delay(500L, TimeUnit.MILLISECONDS)
        )
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

//        add(EventItem(BoardItem.TYPE_EVENT, "1", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("House Party!", 1512993600000L, null, houseLocation, "Celebrate my birthday", "Asia/Bangkok",
//                        false, null, false, false, arrayListOf("yoshi3003", "fatcat18", "fluffy", "panda"))))
//
//        add(EventItem(BoardItem.TYPE_EVENT, "2", createdTime, createdTime, listOf("yoshi3003", "fatcat18"),
//                EventInfo("Dinner", 1513076400000L, 1508594400000L, dinnerLocation, "In many modern usages, the term dinner refers to the evening meal, " +
//                        "which is now often the most significant meal of the day in English-speaking cultures." +
//                        " When this meaning is used, the preceding meals are usually referred to as breakfast, lunch and tea. " +
//                        " The divide between different meanings of \"dinner\" is not cut-and-dried based on either geography or socioeconomic class. " +
//                        "However, the use of the term dinner for the midday meal is strongest among working-class people, especially in the English Midlands, " +
//                        "North of England and the central belt of Scotland.[6] Even in systems in which dinner is the meal usually eaten at the end of the day, " +
//                        "an individual dinner may still refer to a main or more sophisticated meal at any time in the day, such as a banquet, feast, " +
//                        "or a special meal eaten on a Sunday or holiday, such as Christmas dinner or Thanksgiving dinner. At such a dinner, " +
//                        "the people who dine together may be formally dressed and consume food with an array of utensils. " +
//                        "These dinners are often divided into three or more courses. Appetizers consisting of options such as soup or salad, " +
//                        "precede the main course, which is followed by the dessert.\n\n" +
//                        "A survey by Jacob's Creek, an Australian winemaker, found the average evening meal time in the U.K. to be 7:47pm.[7]", "Asia/Bangkok",
//                        false, repeatEvery3Days, false, false, arrayListOf("fatcat18"))))
//
//        add(EventItem(BoardItem.TYPE_EVENT, "3", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("gym", 1512720000000L, null, null, null, "Asia/Bangkok",
//                        false, repeatEveryWeek, false, false, arrayListOf("yoshi3003"))))

        add(EventItem(BoardItem.TYPE_EVENT, "4", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Shopping", null, null, shoppingLocation, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("fatcat18"))))

        add(EventItem(BoardItem.TYPE_EVENT, "5", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("Board game night", null, null, cafeLocation, null, "Asia/Bangkok",
                        false, null, false, false, arrayListOf("yoshi3003", "fluffy", "panda"))))

//        add(EventItem(BoardItem.TYPE_EVENT, "6", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("Beach trip", 1513702800000L, 1514048400000L, null, null, "Asia/Bangkok",
//                        true, null, false, false, arrayListOf("fatcat18"))))
//
//        add(EventItem(BoardItem.TYPE_EVENT, "7", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("Christmas Party", 1514203200000L, null, houseLocation, null, "Asia/Bangkok",
//                        false, null, false, false, arrayListOf("fluffy"))))
//
//        add(EventItem(BoardItem.TYPE_EVENT, "8", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("Office christmas dinner", 1513310400000L, null, null, null, "Asia/Bangkok",
//                        false, null, false, false, arrayListOf("panda"))))
//
//        add(EventItem(BoardItem.TYPE_EVENT, "9", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("Movie - Star Wars: The Last Jedi", 1513429200000L, 1513438200000L, null, null, "Asia/Bangkok",
//                        false, null, false, false, arrayListOf("yoshi3003"))))
//
//        add(EventItem(BoardItem.TYPE_EVENT, "10", createdTime, createdTime, listOf("yoshi3003"),
//                EventInfo("2018 new year party", 1514725200000L, 1514746800000L, condoLocation, null, "Asia/Bangkok",
//                        false, null, false, false, arrayListOf("yoshi3003"))))

        var startTime = Calendar.getInstance().let {
            it.time = Date()
            it.add(Calendar.DAY_OF_YEAR, 3)
            it.set(Calendar.HOUR_OF_DAY, 21)
            it.set(Calendar.MINUTE, 15)
            it.time.time
        }
        add(EventItem(BoardItem.TYPE_EVENT, "11", createdTime, createdTime, listOf("yoshi3003"),
                EventInfo("play game", startTime, null, null, null, "Asia/Bangkok", false,
                        null, false, false, arrayListOf("yoshi3003"))))
    }
}