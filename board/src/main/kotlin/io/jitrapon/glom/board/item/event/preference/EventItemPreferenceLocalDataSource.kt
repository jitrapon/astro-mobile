package io.jitrapon.glom.board.item.event.preference

import android.graphics.Color
import android.util.SparseLongArray
import androidx.core.util.putAll
import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.board.BoardDatabase
import io.jitrapon.glom.board.item.event.CalendarEntity
import io.jitrapon.glom.board.item.event.calendar.CalendarDao
import io.jitrapon.glom.board.item.event.calendar.DeviceCalendar
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.*

class EventItemPreferenceLocalDataSource(database: BoardDatabase,
                                         private val calendarDao: CalendarDao,
                                         private val circleInteractor: CircleInteractor) : EventItemPreferenceDataSource {

    private val preferenceDao = database.eventItemPreferenceDao()

    private var inMemoryPreference: EventItemPreference? = null
    private var inMemorySyncedCalIds = HashSet<String>()
    private val toBeSynced = HashSet<String>()
    private val toBeUnsynced = HashSet<String>()

    override fun getPreference(refresh: Boolean): Flowable<EventItemPreference> {
        return if (!refresh && inMemoryPreference != null) {
            Flowable.fromCallable {
                inMemorySyncedCalIds = inMemoryPreference!!.calendarPreference.calendars
                    .filter { it.isSyncedToBoard }
                    .map { it.calId.toString() }
                    .toHashSet()
                inMemoryPreference
            }
        }
        else Flowable.zip(
                calendarDao.getCalendars().subscribeOn(Schedulers.io()),
                preferenceDao.getSyncedCalendars(circleInteractor.getActiveCircleId()).toFlowable().subscribeOn(Schedulers.io()),
                BiFunction<CalendarPreference, List<CalendarEntity>, EventItemPreference> { deviceCalendars, syncedCalendars ->
                    val list = syncedCalendars.toCalendarList()
                    toBeSynced.clear()
                    toBeUnsynced.clear()
                    inMemorySyncedCalIds = syncedCalendars.map { it.calendarId }.toHashSet()
                    val calendars = list.associateBy({it.calId}, {it}).let { map ->
                        val result = ArrayList<DeviceCalendar>(list)
                        for (calendar in deviceCalendars.calendars) {
                            map[calendar.calId].let {

                                // add a local calendar to our result only
                                // if it's not in the repository's list
                                if (it == null) {
                                    calendar.isLocal = true
                                    calendar.isSyncedToBoard = false
                                    result.add(calendar)
                                }

                                // otherwise, if it's already in the repository
                                // set the flags correctly
                                else {
                                    it.isLocal = true
                                    it.isSyncedToBoard = true
                                    it.accountName = calendar.accountName
                                    it.displayName = calendar.displayName
                                    it.ownerName = calendar.ownerName
                                    it.isVisible = calendar.isVisible
                                    it.color = calendar.color
                                }
                            }
                        }
                        result
                    }
                    EventItemPreference(
                            CalendarPreference(calendars, Date(), deviceCalendars.error),
                            getSyncTime())
                })
                .doOnNext {
                    inMemoryPreference = it
                }
    }

    override fun savePreference(preference: EventItemPreference): Flowable<EventItemPreference> {
        return Flowable.fromCallable {
            if (preference.calendarPreference.calendars.isNotEmpty()) {
                val toBeSyncedCalendars = preference.calendarPreference.calendars.filter {
                    toBeSynced.contains(it.calId.toString())
                }.map { it.toEntity() }
                preferenceDao.insertOrReplaceCalendars(toBeSyncedCalendars)

                val toBeUnsyncedCalendarIds = preference.calendarPreference.calendars.filter {
                    toBeUnsynced.contains(it.calId.toString())
                }.map { it.calId.toString() }
                preferenceDao.deleteCalendars(toBeUnsyncedCalendarIds)
            }
            preference
        }
    }

    override fun getSyncTime(): Date = Date()

    override fun setCalendarSyncStatus(calId: Long, isSynced: Boolean) {
        inMemoryPreference?.calendarPreference?.calendars?.find { it.calId == calId }?.apply {
            isSyncedToBoard = isSynced
            val calIdString = calId.toString()
            if (isSynced) {
                if (!inMemorySyncedCalIds.contains(calIdString)) {
                    toBeSynced.add(calIdString)
                }
                toBeUnsynced.remove(calIdString)
            }
            else {
                if (inMemorySyncedCalIds.contains(calIdString)) {
                    toBeUnsynced.add(calIdString)
                }
                toBeSynced.remove(calIdString)
            }
        }
    }

    override fun getCalendarSyncListDiff(): Pair<HashSet<String>, HashSet<String>> = toBeSynced to toBeUnsynced

    override fun clearCalendarSyncListDiff() {
        toBeSynced.clear()
        toBeUnsynced.clear()
    }

    private fun List<CalendarEntity>.toCalendarList(): List<DeviceCalendar> {
        return map { DeviceCalendar(it.calendarId.toLong(), it.displayName, it.accountName, it.ownerName,
                Color.TRANSPARENT, true, true, it.isLocal) }
    }

    private fun DeviceCalendar.toEntity(): CalendarEntity {
        return CalendarEntity(calId.toString(), displayName, accountName, ownerName, isLocal, circleInteractor.getActiveCircleId())
    }
}
