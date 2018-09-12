package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class UpdatePlaceRequest(@field: Json(name = "location") val location: EventPlacePollRequest?)
