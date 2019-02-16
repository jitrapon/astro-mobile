package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.base.model.SimpleDiffResult
import io.jitrapon.glom.board.item.event.CalendarEntity
import io.jitrapon.glom.board.item.event.calendar.DeviceCalendar
import io.reactivex.Flowable
import java.util.*

/**
 * Main entry to the saved preference settings for event items
 *
 * Created by Jitrapon
 */
interface EventItemPreferenceDataSource {

    fun getPreference(refresh: Boolean): Flowable<EventItemPreference>

    fun savePreference(preference: EventItemPreference): Flowable<EventItemPreference>

    fun getSyncTime(): Date

    fun setCalendarSyncStatus(calId: Long, isSynced: Boolean)

    fun getCalendarDiff(): SimpleDiffResult<String>

    fun clearCalendarDiff()

    fun getSyncedCalendars(): Flowable<List<DeviceCalendar>>
}
