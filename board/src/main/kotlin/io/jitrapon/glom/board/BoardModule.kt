package io.jitrapon.glom.board

import android.app.Application
import androidx.room.Room
import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.model.ContentChangeInfo
import io.jitrapon.glom.board.item.event.EventItemDataSource
import io.jitrapon.glom.board.item.event.EventItemInteractor
import io.jitrapon.glom.board.item.event.EventItemLocalDataSource
import io.jitrapon.glom.board.item.event.EventItemRemoteDataSource
import io.jitrapon.glom.board.item.event.EventItemRepository
import io.jitrapon.glom.board.item.event.calendar.CalendarDao
import io.jitrapon.glom.board.item.event.calendar.CalendarDaoImpl
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceDataSource
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceInteractor
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceLocalDataSource
import io.jitrapon.glom.board.item.event.preference.EventItemPreferenceRepository
import io.reactivex.subjects.PublishSubject

@Module
class BoardModule {

    @Provides
    @BoardScope
    fun provideCalendarDao(application: Application): CalendarDao =
        CalendarDaoImpl(application.applicationContext)

    @Provides
    @BoardScope
    fun provideDatabase(application: Application): BoardDatabase = Room.databaseBuilder(
        application.applicationContext,
        BoardDatabase::class.java,
        "board.db"
    ).build()

    @Provides
    fun provideContentChangeNotifier(): PublishSubject<ContentChangeInfo> = PublishSubject.create()

    @Provides
    @BoardScope
    fun provideBoardDataSource(
        circleInteractor: CircleInteractor,
        database: BoardDatabase,
        userInteractor: UserInteractor,
        eventPref: EventItemPreferenceDataSource,
        calendarDao: CalendarDao,
        contentChangeNotifier: PublishSubject<ContentChangeInfo>
    ): BoardDataSource =
        BoardRepository(
            BoardRemoteDataSource(circleInteractor, contentChangeNotifier),
            BoardLocalDataSource(
                database,
                userInteractor,
                eventPref,
                calendarDao,
                contentChangeNotifier
            ),
            contentChangeNotifier
        )

    @Provides
    @BoardScope
    fun providesEventDataSource(
        userInteractor: UserInteractor,
        circleInteractor: CircleInteractor,
        database: BoardDatabase
    ): EventItemDataSource = EventItemRepository(
        EventItemRemoteDataSource(userInteractor, circleInteractor),
        EventItemLocalDataSource(database, userInteractor)
    )

    @Provides
    @BoardScope
    fun provideBoardInteractor(
        userInteractor: UserInteractor,
        boardDataSource: BoardDataSource,
        circleInteractor: CircleInteractor
    ): BoardInteractor = BoardInteractor(userInteractor, boardDataSource, circleInteractor)

    @Provides
    @BoardScope
    fun provideEventItemInteractor(
        userInteractor: UserInteractor,
        circleInteractor: CircleInteractor,
        boardDataSource: BoardDataSource,
        eventItemDataSource: EventItemDataSource,
        eventItemPreferenceDataSource: EventItemPreferenceDataSource
    ): EventItemInteractor =
        EventItemInteractor(
            userInteractor,
            circleInteractor,
            boardDataSource,
            eventItemDataSource,
            eventItemPreferenceDataSource
        )

    @Provides
    @BoardScope
    fun provideEventItemPreferenceDataSource(
        database: BoardDatabase,
        calendarDao: CalendarDao,
        circleInteractor: CircleInteractor
    ): EventItemPreferenceDataSource = EventItemPreferenceRepository(
        EventItemPreferenceLocalDataSource(database, calendarDao, circleInteractor)
    )

    @Provides
    @BoardScope
    fun provideEventItemPreferenceInteractor(repository: EventItemPreferenceDataSource): EventItemPreferenceInteractor =
        EventItemPreferenceInteractor(repository)
}
