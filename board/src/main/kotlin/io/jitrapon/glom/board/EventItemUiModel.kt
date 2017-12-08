package io.jitrapon.glom.board

import io.jitrapon.glom.base.data.AndroidString
import io.jitrapon.glom.board.BoardItemUiModel.Companion.TYPE_EVENT

/**
 * @author Jitrapon Tiachunpun
 */
data class EventItemUiModel(override val itemId: String?,
                            val title: String,
                            val dateTime: String?,
                            var location: AndroidString?,
                            override val itemType: Int = TYPE_EVENT) : BoardItemUiModel