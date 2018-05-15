package io.jitrapon.glom.base.domain.circle

import com.squareup.moshi.Json

data class CircleInfoResponse(@field: Json(name = "circle_id") val id: String,
                              @field: Json(name = "name") val name: String,
                              @field: Json(name = "avatar") val avatar: String?,
                              @field: Json(name = "info") val info: String?,
                              @field: Json(name = "interests") val interests: List<String>,
                              @field: Json(name = "repeat") val repeatInfo: RepeatInfoResponse?,
                              @field: Json(name = "places") val places: List<PlaceInfoResponse>)

data class RepeatInfoResponse(@field: Json(name = "unit") val unit: Int,
                              @field: Json(name = "interval") val interval: Long,
                              @field: Json(name = "until") val until: Long,
                              @field: Json(name = "meta") val meta: List<Int>?)

data class PlaceInfoResponse(@field: Json(name = "name") val name: String?,
                             @field: Json(name = "description") val description: String?,
                             @field: Json(name = "avatar") val avatar: String?,
                             @field: Json(name = "lat") val lat: Double?,
                             @field: Json(name = "long") val long: Double?,
                             @field: Json(name = "g_place_id") val googlePlaceId: String?,
                             @field: Json(name = "place_id") val placeId: String,
                             @field: Json(name = "status") val status: Int)