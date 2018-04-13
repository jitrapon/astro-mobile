package io.jitrapon.glom.base.di

import android.app.Application
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
}