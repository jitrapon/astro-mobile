package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class UpdatePlacePollStatusRequest(@field: Json(name = "is_place_poll_opened") val placePollStatus: Boolean)
