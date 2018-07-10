package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class UpdatePollCountRequest(@field: Json(name = "increment") val increment: Boolean)