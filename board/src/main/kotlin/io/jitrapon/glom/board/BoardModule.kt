package io.jitrapon.glom.board

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.UserDataSource
import io.jitrapon.glom.board.event.EventItemInteractor

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideBoardInteractor(userDataSource: UserDataSource): BoardInteractor = BoardInteractor(userDataSource)

    @Provides
    @BoardScope
    fun provideEventItemInteractor(userDataSource: UserDataSource): EventItemInteractor = EventItemInteractor(userDataSource)
}
