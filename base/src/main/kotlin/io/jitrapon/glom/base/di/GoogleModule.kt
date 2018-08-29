package io.jitrapon.glom.base.di

import android.app.Application
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.component.GooglePlaceProvider
import io.jitrapon.glom.base.component.PlaceProvider
import javax.inject.Singleton

@Module(includes = [BaseModule::class])
class GoogleModule {

    @Provides
    @Singleton
    fun providePlaceProvider(application: Application): PlaceProvider = GooglePlaceProvider(context = application)
}
