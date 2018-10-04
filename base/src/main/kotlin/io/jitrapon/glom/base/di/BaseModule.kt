package io.jitrapon.glom.base.di

import android.accounts.AccountManager
import android.app.Application
import androidx.core.content.getSystemService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Application-wide module
 *
 * @author Jitrapon Tiachunpun
 */
@Module
class BaseModule(val application: Application) {

    @Provides
    @Singleton
    fun provideApplication(): Application = application

    @Provides
    fun provideAccountManager(): AccountManager = application.getSystemService<AccountManager>()!!
}