package io.jitrapon.glom.base.domain.user

import com.squareup.moshi.Json

/**
 * Json data class does not need to have nullable fields, since Moshi
 * assigns null values to their backing fields using reflection anyway
 */
data class UsersResponse(@field:Json(name = "users") val users: List<UserResponse>)

data class UserResponse(@field:Json(name = "user_id") val userId: String,
                        @field:Json(name = "user_name") val userName: String,
                        @field:Json(name = "gcm_token") val gcmToken: String?,
                        @field:Json(name = "avatar") val avatar: String?,
                        @field:Json(name = "userType") val userType: Int)
