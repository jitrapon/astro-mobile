package io.jitrapon.glom.base.di

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.*
import javax.inject.Singleton

@Module
class BaseDomainModule {

    @Provides
    @Singleton
    fun provideUserRepository(): UserDataSource = UserRepository()

    @Provides
    @Singleton
    fun provideCircleRepository(): CircleDataSource = CircleRepository()

    @Provides
    @Singleton
    fun provideCircleInteractor(dataSource: CircleDataSource): CircleInteractor = CircleInteractor(dataSource)
}