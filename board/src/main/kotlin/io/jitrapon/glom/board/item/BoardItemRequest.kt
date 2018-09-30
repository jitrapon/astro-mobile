package io.jitrapon.glom.board.item

import com.squareup.moshi.Json

/**
 * Generic board item request
 */
data class BoardItemRequest(@field:Json(name = "item_id") val itemId: String,
                            @field:Json(name = "item_type") val itemType: Int,
                            @field:Json(name = "time") val time: Long?,
                            @field:Json(name = "owners") val owners: List<String>,
                            @field:Json(name = "info") val info: Map<String, Any?>)