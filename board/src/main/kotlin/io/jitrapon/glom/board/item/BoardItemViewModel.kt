package io.jitrapon.glom.board.item

import io.jitrapon.glom.base.model.LiveEvent
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.board.BoardInteractor

/**
 * @author Jitrapon Tiachunpun
 */
abstract class BoardItemViewModel : BaseViewModel() {

    /* whether or not this is a new item to add */
    internal var isNewItem: Boolean = false

    /* whether or not this item is editable */
    internal var isItemEditable: Boolean = false

    /**
     * Converts a Model to UiModel
     */
    abstract fun toUiModel(item: BoardItem, syncStatus: SyncStatus): BoardItemUiModel

    /**
     * Converts editable status of the BoradItem to UiModel status
     */
    fun getEditableStatus(allow: Boolean): UiModel.Status =
        if (allow) UiModel.Status.SUCCESS else UiModel.Status.NEGATIVE

    /**
     * Converts the sync status to UiModel status
     */
    open fun getSyncStatus(status: SyncStatus): UiModel.Status {
        return when (status) {
            SyncStatus.OFFLINE -> UiModel.Status.POSITIVE
            SyncStatus.ACTIVE -> UiModel.Status.LOADING
            SyncStatus.SUCCESS -> UiModel.Status.SUCCESS
            SyncStatus.FAILED -> UiModel.Status.ERROR
        }
    }

    /**
     * Updates the sync status after a sync operation has completed
     *
     * @param isSynced whether or not the sync operation to its respective repository is successful
     */
    abstract fun updateSyncStatus(
        item: BoardItem,
        itemUiModel: BoardItemUiModel,
        boardInteractor: BoardInteractor,
        isSynced: Boolean
    )

    /**
     * Call before deleting to show any View to let the users know about the delete operation
     * or choose a delete operation type
     */
    abstract fun prepareItemToDelete(observable: LiveEvent<UiActionModel>, itemId: String, onReady: () -> Unit)

    /**
     * Call to clean up any resources
     */
    abstract fun cleanUp()
}
