package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class UpdateDateRequest(@field: Json(name = "start_date") val startDate: Long?,
                             @field: Json(name = "end_date") val endDate: Long?)