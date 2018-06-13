package io.jitrapon.glom.base.di

import android.app.Application
import android.arch.persistence.room.Room
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.BaseDatabase
import io.jitrapon.glom.base.domain.circle.CircleDataSource
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.circle.CircleRemoteDataSource
import io.jitrapon.glom.base.domain.circle.CircleRepository
import io.jitrapon.glom.base.domain.user.*
import javax.inject.Singleton

@Module(includes = [BaseModule::class])
class BaseDomainModule {

    @Provides
    @Singleton
    fun provideDatabase(application: Application): BaseDatabase = Room.databaseBuilder(application.applicationContext, BaseDatabase::class.java, "base.db").build()

    @Provides
    @Singleton
    fun provideUserRepository(database: BaseDatabase): UserDataSource = UserRepository(UserRemoteDataSource(), UserLocalDataSource(database))

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