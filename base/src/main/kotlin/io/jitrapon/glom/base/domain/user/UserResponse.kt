package io.jitrapon.glom.base.domain.user

import com.squareup.moshi.Json

/**
 * Json data class does not need to have nullable fields, since Moshi
 * assigns null values to their backing fields using reflection anyway
 */
data class UserResponse(@field:Json(name = "id") val id: String,
                        @field:Json(name = "name") val name: String)
