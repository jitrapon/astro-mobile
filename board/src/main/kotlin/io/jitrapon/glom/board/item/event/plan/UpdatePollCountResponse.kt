package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class UpdatePollCountResponse(@field: Json(name = "users") val users: List<String>)