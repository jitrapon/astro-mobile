package io.jitrapon.glom.base.di

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.UserDataSource
import io.jitrapon.glom.base.domain.UserRepository
import javax.inject.Singleton

@Module
class BaseDomainModule {

    @Provides
    @Singleton
    fun provideUserRepository(): UserDataSource = UserRepository()
}