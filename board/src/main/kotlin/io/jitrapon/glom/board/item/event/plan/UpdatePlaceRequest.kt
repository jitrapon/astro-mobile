package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class UpdatePlaceRequest(@field: Json(name = "location") val location: EventPlacePollRequest?)
