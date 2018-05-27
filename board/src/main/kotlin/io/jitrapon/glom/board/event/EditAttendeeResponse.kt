package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class EditAttendeeResponse(@Json(name = "user_id") val userId: String?,
                                @Json(name = "status") val status: Int?)
