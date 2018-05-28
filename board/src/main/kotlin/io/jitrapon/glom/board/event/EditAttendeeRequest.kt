package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class EditAttendeeRequest(@Json(name = "status") val status: Int)