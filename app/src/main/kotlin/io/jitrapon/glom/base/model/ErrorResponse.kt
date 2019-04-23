package io.jitrapon.glom.base.model

import com.squareup.moshi.Json

/**
 * Common error response JSON from remote source
 *
 * @author Jitrapon Tiachunpun
 */
data class ErrorResponse(@field: Json(name = "error") val error: String? /* optional error message */)
