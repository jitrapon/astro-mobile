package io.jitrapon.glom.board.event

import com.squareup.moshi.Json

data class UpdateDatePollStatusRequest(@field: Json(name = "is_date_poll_opened") val datePollStatus: Boolean)
