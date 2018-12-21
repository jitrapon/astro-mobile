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
                                      @field:Json(name = "id_token") private val idToken: String?)

data class SignInWithOAuthCredentialRequest(@field:Json(name = "credential") private val token: String,
                                            @field:Json(name = "provider_id") private val providerId: String)

data class SignInWithOAuthCredentialResponse(@field:Json(name = "id_token") val idToken: String,
                                             @field:Json(name = "refresh_token") val refreshToken: String,
                                             @field:Json(name = "expires_in") val expireTime: Long,
                                             @field:Json(name = "user_id") val userId: String,
                                             @field:Json(name = "is_anonymous") val isAnonymous: Boolean? = null,
                                             @field:Json(name = "provider_id") val providerId: String,
                                             @field:Json(name = "full_name") val fullName: String?,
                                             @field:Json(name = "email") val email: String?,
                                             @field:Json(name = "email_verified") val isEmailVerified: Boolean,
                                             @field:Json(name = "display_name") val displayName: String?,
                                             @field:Json(name = "photo_url") val photoUrl: String?,
                                             @field:Json(name = "need_confirmation") val requireConfirmation: Boolean)
