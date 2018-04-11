package io.jitrapon.glom.board

import dagger.Component
import io.jitrapon.glom.base.di.AppModule
import io.jitrapon.glom.base.di.BaseDomainModule
import io.jitrapon.glom.board.event.EventItemInteractor

@Component(modules = [AppModule::class, BaseDomainModule::class])
interface BoardComponent {

    fun inject(interactor: BoardInteractor)
    fun inject(interactor: EventItemInteractor)
}