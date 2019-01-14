package io.jitrapon.glom.board.item

import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * @author Jitrapon Tiachunpun
 */
abstract class BoardItemViewModel : BaseViewModel() {

    /**
     * Converts a Model to UiModel
     */
    abstract fun toUiModel(item: BoardItem, syncStatus: SyncStatus): BoardItemUiModel

    /**
     * Converts the sync status to UiModel status
     */
    fun getSyncStatus(status: SyncStatus): UiModel.Status {
        return when (status) {
            SyncStatus.OFFLINE -> UiModel.Status.POSITIVE
            SyncStatus.ACTIVE -> UiModel.Status.LOADING
            SyncStatus.SUCCESS -> UiModel.Status.SUCCESS
            SyncStatus.FAILED -> UiModel.Status.ERROR
        }
    }

    /**
     * Call to clean up any resources
     */
    abstract fun cleanUp()
}
