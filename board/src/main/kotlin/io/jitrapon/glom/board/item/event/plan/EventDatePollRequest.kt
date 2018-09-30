package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class EventDatePollRequest(@field: Json(name = "start_time") val startDate: Long?,
                                @field: Json(name = "end_time") val endDate: Long?)