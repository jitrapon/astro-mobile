package io.jitrapon.glom.base.domain.user.account

import com.squareup.moshi.Json

/**
 * Created by Jitrapon
 */
data class RefreshIdTokenRequest(@field:Json(name = "refresh_token") private val refreshToken: String?)

data class AccountInfoResponse(@field:Json(name = "id_token") val idToken: String,
                               @field:Json(name = "refresh_token") val refreshToken: String,
                               @field:Json(name = "expires_in") val expireTime: Long,
                               @field:Json(name = "user_id") val userId: String)