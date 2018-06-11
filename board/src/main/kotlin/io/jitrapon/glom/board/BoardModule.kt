package io.jitrapon.glom.board

import android.app.Application
import android.arch.persistence.room.Room
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.event.EventItemDataSource
import io.jitrapon.glom.board.event.EventItemInteractor
import io.jitrapon.glom.board.event.EventItemRemoteDataSource
import io.jitrapon.glom.board.event.EventItemRepository

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideDatabase(application: Application): BoardDatabase = Room.databaseBuilder(application.applicationContext, BoardDatabase::class.java, "board.db").build()

    @Provides
    @BoardScope
    fun provideBoardDataSource(circleInteractor: CircleInteractor, database: BoardDatabase): BoardDataSource = BoardRepository(BoardRemoteDataSource(circleInteractor), BoardLocalDataSource(database))

    @Provides
    @BoardScope
    fun providesEventDataSource(userInteractor: UserInteractor, circleInteractor: CircleInteractor): EventItemDataSource = EventItemRepository(EventItemRemoteDataSource(userInteractor, circleInteractor))

    @Provides
    @BoardScope
    fun provideBoardInteractor(userInteractor: UserInteractor, boardDataSource: BoardDataSource, circleInteractor: CircleInteractor): BoardInteractor
            = BoardInteractor(userInteractor, boardDataSource, circleInteractor)

    @Provides
    @BoardScope
    fun provideEventItemInteractor(userInteractor: UserInteractor, circleInteractor: CircleInteractor, boardDataSource: BoardDataSource, eventItemDataSource: EventItemDataSource): EventItemInteractor =
            EventItemInteractor(userInteractor, circleInteractor, boardDataSource, eventItemDataSource)
}
