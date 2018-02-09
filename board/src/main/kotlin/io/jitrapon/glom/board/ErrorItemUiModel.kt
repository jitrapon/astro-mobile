package io.jitrapon.glom.board

import com.google.android.gms.location.places.Place
import io.jitrapon.glom.base.model.UiModel

/**
 * @author Jitrapon Tiachunpun
 */
data class ErrorItemUiModel(override val itemType: Int = BoardItemUiModel.TYPE_ERROR,
                            override val itemId: String? = null,
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