package io.jitrapon.glom.board

import com.google.android.gms.maps.model.LatLng
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.board.item.BoardItemUiModel
import java.util.ArrayList

/**
 * @author Jitrapon Tiachunpun
 */
data class HeaderItemUiModel(val text: AndroidString,
                             override val itemType: Int = BoardItemUiModel.TYPE_HEADER,
                             override val itemId: String = text.hashCode().toString(),
                             override var status: UiModel.Status = UiModel.Status.SUCCESS) : BoardItemUiModel {

    override fun getStatusChangePayload(): Int {
        return 0
    }

    override fun getChangePayload(other: BoardItemUiModel?): List<Int> {
        return ArrayList()
    }

    override fun updateLocationText(name: String?): Int {
        //not applicable
        return 0
    }

    override fun updateLocationLatLng(latLng: LatLng?): Int {
        //not applicable
        return 0
    }
}
