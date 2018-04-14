package io.jitrapon.glom.base.di

import android.app.Application
import dagger.Component
import io.jitrapon.glom.base.domain.CircleDataSource
import io.jitrapon.glom.base.domain.CircleInteractor
import io.jitrapon.glom.base.domain.UserDataSource
import javax.inject.Singleton

/**
 * This component provides objects that live as long as the Application itself
 * They are 'true' singletons
 *
 * Created by Jitrapon
 */
@Singleton
@Component(modules = [BaseModule::class, BaseDomainModule::class])
interface BaseComponent {

    fun application(): Application
    fun userDataSource(): UserDataSource
    fun circleDataSource(): CircleDataSource
    fun circleInteractor(): CircleInteractor
}
