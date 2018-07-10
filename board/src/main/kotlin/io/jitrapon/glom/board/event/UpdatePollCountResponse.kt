package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class UpdatePollCountResponse(@field: Json(name = "users") val users: List<String>)