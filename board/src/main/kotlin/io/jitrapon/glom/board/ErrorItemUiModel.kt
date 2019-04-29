package io.jitrapon.glom.board

import com.google.android.libraries.places.api.model.Place
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.board.item.BoardItemUiModel
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */
data class ErrorItemUiModel(override val itemId: String,
                            override val itemType: Int = BoardItemUiModel.TYPE_ERROR,
                            override var status: UiModel.Status = UiModel.Status.SUCCESS) : BoardItemUiModel {

    override fun getChangePayload(other: BoardItemUiModel?): List<Int> {
        return ArrayList()
    }

    override fun updateLocationText(place: Place?): Int {
        //not applicable
        return 0
    }

    override fun getStatusChangePayload(): Int = 0
}
