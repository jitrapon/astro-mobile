package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class UpdatePlacePollStatusRequest(@Json(name = "is_place_poll_opened") val placePollStatus: Boolean)
