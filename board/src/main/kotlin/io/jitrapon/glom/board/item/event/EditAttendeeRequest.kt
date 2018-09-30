package io.jitrapon.glom.board.item.event

import com.squareup.moshi.Json

data class EditAttendeeRequest(@Json(name = "status") val status: Int)