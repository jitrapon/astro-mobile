package io.jitrapon.glom.board

import android.app.Application
import android.arch.persistence.room.Room
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.event.*

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideDatabase(application: Application): BoardDatabase = Room.databaseBuilder(application.applicationContext, BoardDatabase::class.java, "board.db").build()

    @Provides
    @BoardScope
    fun provideBoardDataSource(circleInteractor: CircleInteractor, database: BoardDatabase, userInteractor: UserInteractor): BoardDataSource =
            BoardRepository(BoardRemoteDataSource(circleInteractor), BoardLocalDataSource(database, userInteractor))

    @Provides
    @BoardScope
    fun providesEventDataSource(userInteractor: UserInteractor, circleInteractor: CircleInteractor, database: BoardDatabase): EventItemDataSource = EventItemRepository(EventItemRemoteDataSource(userInteractor, circleInteractor), EventItemLocalDataSource(database, userInteractor))

    @Provides
    @BoardScope
    fun provideBoardInteractor(userInteractor: UserInteractor, boardDataSource: BoardDataSource, circleInteractor: CircleInteractor): BoardInteractor
            = BoardInteractor(userInteractor, boardDataSource, circleInteractor)

    @Provides
    @BoardScope
    fun provideEventItemInteractor(userInteractor: UserInteractor, circleInteractor: CircleInteractor, boardDataSource: BoardDataSource, eventItemDataSource: EventItemDataSource): EventItemInteractor =
            EventItemInteractor(userInteractor, circleInteractor, boardDataSource, eventItemDataSource)
}
