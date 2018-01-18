package io.jitrapon.glom.board

import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Flowable

/**
 * Retrieves, stores, and saves the state of a board item
 *
 * Created by Jitrapon
 */
class BoardItemRepository : Repository<BoardItem>() {

    private var item: BoardItem? = null

    override fun load(): Flowable<BoardItem> {
        throw NotImplementedError()
    }

    override fun loadList(): Flowable<List<BoardItem>> {
        throw NotImplementedError()
    }

    fun setCache(boardItem: BoardItem) {
        item = boardItem
    }

    fun getCache(): BoardItem? = item

    fun save(info: BoardItemInfo) {
        item?.setInfo(info)
    }
}
