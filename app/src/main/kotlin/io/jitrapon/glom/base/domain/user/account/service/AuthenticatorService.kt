package io.jitrapon.glom.base.domain.user.account.service

import android.accounts.AbstractAccountAuthenticator
import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Created by Jitrapon
 */
class AuthenticatorService : Service() {

    private lateinit var authenticator: AbstractAccountAuthenticator

    override fun onCreate() {
        authenticator = Authenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        return authenticator.iBinder
    }
}