package io.jitrapon.glom.board

import android.app.Application
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.item.event.*

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideCalendarDao(application: Application): CalendarDao = CalendarDaoImpl(application.applicationContext)

    @Provides
    @BoardScope
    fun provideDatabase(application: Application): BoardDatabase = Room.databaseBuilder(application.applicationContext, BoardDatabase::class.java, "board.db").build()

    @Provides
    @BoardScope
    fun provideBoardDataSource(circleInteractor: CircleInteractor, calendarDao: CalendarDao, database: BoardDatabase, userInteractor: UserInteractor): BoardDataSource =
            BoardRepository(BoardRemoteDataSource(circleInteractor), BoardLocalDataSource(database, calendarDao, userInteractor))

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
