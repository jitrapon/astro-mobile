package io.jitrapon.glom.base.domain.user.account

import com.squareup.moshi.Json

/**
 * Created by Jitrapon
 */
data class RefreshIdTokenRequest(@field:Json(name = "refresh_token") private val refreshToken: String?)

data class AccountInfoResponse(@field:Json(name = "id_token") val idToken: String,
                               @field:Json(name = "refresh_token") val refreshToken: String,
                               @field:Json(name = "expires_in") val expireTime: Long,
                               @field:Json(name = "user_id") val userId: String,
                               @field:Json(name = "is_anonymous") val isAnonymous: Boolean? = null)

data class SignInEmailPasswordRequest(@field:Json(name = "email") private val email: String,
                                      @field:Json(name = "password") private val password: String)

data class SignUpEmailPasswordRequest(@field:Json(name = "email") private val email: String,
                                      @field:Json(name = "password") private val password: String,
                                      @field:Json(name = "idToken") private val idToken: String?)
