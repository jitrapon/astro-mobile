package io.jitrapon.glom.board.item.event.plan

import com.squareup.moshi.Json

data class UpdateDatePollStatusRequest(@field: Json(name = "is_date_poll_opened") val datePollStatus: Boolean)
