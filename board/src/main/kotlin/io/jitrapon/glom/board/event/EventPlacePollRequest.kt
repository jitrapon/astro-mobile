package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class EventPlacePollRequest(@field: Json(name = "place_id") val placeId: String?,
                                 @field: Json(name = "google_place_id") val googlePlaceId: String?)
