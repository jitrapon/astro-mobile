package io.jitrapon.glom.board

import android.app.Application
import androidx.room.Room
import dagger.Lazy
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.board.item.event.*
import io.jitrapon.glom.board.item.event.calendar.CalendarDao
import io.jitrapon.glom.board.item.event.calendar.CalendarDaoImpl
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceDataSource
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceInteractor
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceLocalDataSource
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceRepository

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideCalendarDao(application: Application): CalendarDao =
        CalendarDaoImpl(application.applicationContext)

    @Provides
    @BoardScope
    fun provideDatabase(application: Application): BoardDatabase = Room.databaseBuilder(application.applicationContext, BoardDatabase::class.java, "board.db").build()

    @Provides
    @BoardScope
    fun provideBoardDataSource(circleInteractor: CircleInteractor, database: BoardDatabase, userInteractor: UserInteractor,
                               eventPref: EventItemPreferenceDataSource, calendarDao: CalendarDao): BoardDataSource =
            BoardRepository(BoardRemoteDataSource(circleInteractor), BoardLocalDataSource(database, userInteractor, eventPref, calendarDao))

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

    @Provides
    @BoardScope
    fun provideEventItemPreferenceDataSource(database: BoardDatabase, calendarDao: CalendarDao, circleInteractor: CircleInteractor): EventItemPreferenceDataSource = EventItemPreferenceRepository(
            EventItemPreferenceLocalDataSource(database, calendarDao, circleInteractor))

    @Provides
    @BoardScope
    fun provideEventItemPreferenceInteractor(repository: EventItemPreferenceDataSource): EventItemPreferenceInteractor =
            EventItemPreferenceInteractor(repository)
}
