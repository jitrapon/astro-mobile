package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class GetDatePollResponse(@field: Json(name = "dates") val dates: List<EventDatePollResponse>)

data class EventDatePollResponse(@field: Json(name = "poll_id") val pollId: String,
                                 @field: Json(name = "start_time") val startTime: Long,
                                 @field: Json(name = "end_time") val endTime: Long?,
                                 @field: Json(name = "users") val users: List<String>)
