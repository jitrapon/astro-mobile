package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class UpdatePollCountRequest(@field: Json(name = "increment") val increment: Boolean)