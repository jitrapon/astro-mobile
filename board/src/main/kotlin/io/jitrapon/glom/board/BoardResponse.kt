package io.jitrapon.glom.board

import com.squareup.moshi.Json

/**
 * JSON intermediate class for deserializing Board data
 */
data class BoardResponse(@field:Json(name = "board_id") val boardId: String,
                         @field:Json(name = "items") val items: List<BoardItemResponse>)

/**
 * All board item json body must implement this interface
 */
data class BoardItemResponse(@field:Json(name = "item_id") val itemId: String,
                             @field:Json(name = "item_type") val itemType: Int,
                             @field:Json(name = "created_time") val createdTime: Long?,
                             @field:Json(name = "updated_time") val updatedTime: Long?,
                             @field:Json(name = "owners") val owners: List<String>,
                             @field:Json(name = "info") val info: Map<String, Any>)