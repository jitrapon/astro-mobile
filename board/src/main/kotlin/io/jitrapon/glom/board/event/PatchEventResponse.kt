package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class PatchEventResponse(@field: Json(name = "start_time") val startTime: Long?,
                              @field: Json(name = "end_time") val endTime: Long?,
                              @field: Json(name = "location") val location: PatchEventLocationResponse?,
                              @field: Json(name = "is_date_poll_opened") val datePollStatus: Boolean,
                              @field: Json(name = "is_place_poll_opened") val placePollStatus: Boolean)

data class PatchEventLocationResponse(@field: Json(name = "lat") val latitude: Double?,
                                      @field: Json(name = "long") val longitude: Double?,
                                      @field: Json(name = "g_place_id") val googlePlaceId: String?,
                                      @field: Json(name = "place_id") val placeId: String?)
