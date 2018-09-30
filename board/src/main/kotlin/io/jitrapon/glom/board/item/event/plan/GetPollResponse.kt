package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class GetDatePollResponse(@field: Json(name = "dates") val dates: List<EventDatePollResponse>)

data class EventDatePollResponse(@field: Json(name = "poll_id") val pollId: String,
                                 @field: Json(name = "start_time") val startTime: Long,
                                 @field: Json(name = "end_time") val endTime: Long?,
                                 @field: Json(name = "users") val users: List<String>)

data class GetPlacePollResponse(@field: Json(name = "places") val places: List<EventPlacePollResponse>)

data class EventPlacePollResponse(@field: Json(name = "poll_id") val pollId: String,
                                  @field: Json(name = "name") val name: String?,
                                  @field: Json(name = "description") val description: String?,
                                  @field: Json(name = "address") val address: String?,
                                  @field: Json(name = "avatar") val avatar: String?,
                                  @field: Json(name = "latitude") val latitude: Double?,
                                  @field: Json(name = "longitude") val longitude: Double?,
                                  @field: Json(name = "place_id") val placeId: String?,
                                  @field: Json(name = "g_place_id") val googlePlaceId: String?,
                                  @field: Json(name = "is_ai_suggested") val isAiSuggested: Boolean,
                                  @field: Json(name = "users") val users: List<String>)