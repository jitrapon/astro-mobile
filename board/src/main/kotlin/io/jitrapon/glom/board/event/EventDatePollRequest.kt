package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class EventDatePollRequest(@field: Json(name = "start_time") val startDate: Long?,
                                @field: Json(name = "end_time") val endDate: Long?)