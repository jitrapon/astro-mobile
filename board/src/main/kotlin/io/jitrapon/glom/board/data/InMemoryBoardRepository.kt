package io.jitrapon.glom.board.data

import io.jitrapon.glom.base.data.Repository
import io.jitrapon.glom.base.data.SourceType
import io.reactivex.Flowable
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */
class InMemoryBoardRepository : Repository<Board>() {

    override val type: SourceType = SourceType.CACHE

    override fun load(): Flowable<Board> = Flowable.just(Board("abcd1234", getItems()))

    private fun getItems() = ArrayList<BoardItem>().apply {
        val houseLocation = EventLocation(13.732756, 100.643237, null, null)
        val dinnerLocation = EventLocation(null, null, "ChIJhfF-wgOf4jARwAQMPbMAAQ8", null)

        val repeatEvery3Days = RepeatInfo(19, false, 0, 3, 0L, null)
        val repeatEveryWeek = RepeatInfo(1, false, 1, 1, 0L, listOf(1, 3, 5))

        add(EventItem(BoardItem.TYPE_EVENT, "1", 1505874600000L, 1505874600000L, listOf("yoshi3003"),
                EventInfo("House Party!", 1506078000000L, null, houseLocation, "Celebrate my birthday", "Asia/Bangkok",
                        false, null, false, false, listOf("yoshi3003"))))
        add(EventItem(BoardItem.TYPE_EVENT, "2", 1508230200000L, 1508230200000L, listOf("yoshi3003", "fatcat18"),
                EventInfo("Dinner", 1508587200000L, 1508594400000L, dinnerLocation, null, "Asia/Bangkok",
                        false, repeatEvery3Days, false, false, listOf("yoshi3003", "fatcat18"))))
        add(EventItem(BoardItem.TYPE_EVENT, "3", 1508230200000L, 1508230200000L, listOf("yoshi3003"),
                EventInfo("gym", 1511353800000L, null, null, null, "Asia/Bangkok",
                        false, repeatEveryWeek, false, false, listOf("yoshi3003"))))
    }
}