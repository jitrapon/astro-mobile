package io.jitrapon.glom.board.item

import android.util.SparseArray
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventItemViewModel

/**
 * Singleton view model stores to reuse ViewModel instances for specific
 * board items
 *
 * @author Jitrapon Tiachunpun
 */
object BoardItemViewModelStore {

    /* Stores item-specific ViewModel instances */
    private val itemViewModelStore = SparseArray<BoardItemViewModel>(0)

    /**
     * Retrieves a ViewModel instance for a BoardItem. If one is not created, it will be
     * instantiated first and returned.
     */
    fun <T : BoardItem> obtainViewModelForItem(clazz: Class<T>): BoardItemViewModel? {
        return when (clazz) {
            EventItem::class.java -> {
                var viewModel = itemViewModelStore[BoardItem.TYPE_EVENT]
                if (viewModel == null) {
                    viewModel = EventItemViewModel()
                    itemViewModelStore.put(BoardItem.TYPE_EVENT, viewModel)
                }
                viewModel
            }
            else -> null
        }
    }

    fun clear() {
        for (i in 0 until itemViewModelStore.size()) {
            itemViewModelStore[i]?.cleanUp()
        }
        itemViewModelStore.clear()
    }
}
