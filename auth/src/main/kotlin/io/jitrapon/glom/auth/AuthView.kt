package io.jitrapon.glom.auth

import android.content.Intent

/**
 * Abstraction interface required by the BaseOauthInteractor
 */
interface AuthView {

    fun onRequireSignIn(intent: Intent)
}
