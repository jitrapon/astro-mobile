package io.jitrapon.glom.board

import io.jitrapon.glom.board.BoardItemUiModel.Companion.TYPE_EVENT

/**
 * @author Jitrapon Tiachunpun
 */
data class EventItemUiModel(val name: String,
                            val startTime: String?,
                            val endTime: String?,
                            override val itemType: Int = TYPE_EVENT) : BoardItemUiModel