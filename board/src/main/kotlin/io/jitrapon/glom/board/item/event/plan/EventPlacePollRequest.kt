package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class EventPlacePollRequest(@field: Json(name = "place_id") val placeId: String?,
                                 @field: Json(name = "g_place_id") val googlePlaceId: String?)
