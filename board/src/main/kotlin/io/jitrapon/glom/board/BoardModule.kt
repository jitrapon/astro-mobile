package io.jitrapon.glom.board

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserDataSource
import io.jitrapon.glom.board.event.EventItemDataSource
import io.jitrapon.glom.board.event.EventItemInteractor
import io.jitrapon.glom.board.event.EventItemRepository

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideBoardDataSource(): BoardDataSource = BoardRepository()

    @Provides
    @BoardScope
    fun providesEventDataSource(): EventItemDataSource = EventItemRepository()

    @Provides
    @BoardScope
    fun provideBoardInteractor(userDataSource: UserDataSource, boardDataSource: BoardDataSource, circleInteractor: CircleInteractor): BoardInteractor
            = BoardInteractor(userDataSource, boardDataSource, circleInteractor)

    @Provides
    @BoardScope
    fun provideEventItemInteractor(userDataSource: UserDataSource, circleInteractor: CircleInteractor, boardDataSource: BoardDataSource, eventItemDataSource: EventItemDataSource): EventItemInteractor =
            EventItemInteractor(userDataSource, circleInteractor, boardDataSource, eventItemDataSource)
}
