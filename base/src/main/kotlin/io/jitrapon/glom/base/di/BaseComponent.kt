package io.jitrapon.glom.base.di

import android.app.Application
import dagger.Component
import io.jitrapon.glom.base.domain.circle.CircleDataSource
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserDataSource
import io.jitrapon.glom.base.repository.RemoteDataSource
import javax.inject.Singleton

/**
 * This component provides objects that live as long as the Application itself
 * They are 'true' singletons
 *
 * Created by Jitrapon
 */
@Singleton
@Component(modules = [BaseModule::class, BaseDomainModule::class, NetModule::class])
interface BaseComponent {

    fun application(): Application
    fun userDataSource(): UserDataSource
    fun circleDataSource(): CircleDataSource
    fun circleInteractor(): CircleInteractor

    fun inject(dataSource: RemoteDataSource)
}
