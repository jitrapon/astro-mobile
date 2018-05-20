package io.jitrapon.glom.base.di

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleDataSource
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.circle.CircleRemoteDataSource
import io.jitrapon.glom.base.domain.circle.CircleRepository
import io.jitrapon.glom.base.domain.user.UserDataSource
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.domain.user.UserRemoteDataSource
import io.jitrapon.glom.base.domain.user.UserRepository
import javax.inject.Singleton

@Module
class BaseDomainModule {

    @Provides
    @Singleton
    fun provideUserRepository(): UserDataSource = UserRepository(UserRemoteDataSource())

    @Provides
    @Singleton
    fun provideUserInteractor(dataSource: UserDataSource): UserInteractor = UserInteractor(dataSource)

    @Provides
    @Singleton
    fun provideCircleRepository(): CircleDataSource = CircleRepository(CircleRemoteDataSource())

    @Provides
    @Singleton
    fun provideCircleInteractor(circleDataSource: CircleDataSource, userDataSource: UserDataSource):
            CircleInteractor = CircleInteractor(circleDataSource, userDataSource)
}