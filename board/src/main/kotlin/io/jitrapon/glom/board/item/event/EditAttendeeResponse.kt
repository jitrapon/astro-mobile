package io.jitrapon.glom.board.item.event

import com.squareup.moshi.Json

data class EditAttendeeResponse(@field: Json(name = "user_id") val userId: String?,
                                @field: Json(name = "status") val status: Int?)
