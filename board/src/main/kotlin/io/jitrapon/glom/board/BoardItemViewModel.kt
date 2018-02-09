package io.jitrapon.glom.board

import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.viewmodel.BaseViewModel

/**
 * @author Jitrapon Tiachunpun
 */
abstract class BoardItemViewModel : BaseViewModel() {

    /**
     * Converts a Model to UiModel
     */
    abstract fun toUiModel(item: BoardItem, status: UiModel.Status): BoardItemUiModel

    /**
     * Call to clean up any resources
     */
    abstract fun cleanUp()
}