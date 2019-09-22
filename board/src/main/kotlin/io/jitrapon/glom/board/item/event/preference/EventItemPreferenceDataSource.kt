package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.base.model.SimpleDiffResult
import io.jitrapon.glom.board.item.event.EventSource
import io.jitrapon.glom.board.item.event.calendar.DeviceCalendar
import io.reactivex.Flowable
import java.util.Date

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

    fun getSyncedSources(): Flowable<List<EventSource>>
}

const val EVENT_ITEM_MAP_USE_GOOGLE_MAP = false
const val EVENT_ITEM_MAP_CAMERA_ZOOM_LEVEL = 15f
const val EVENT_ITEM_ATTENDEE_MAXIMUM_COUNT = 3
const val EVENT_ITEM_MAX_FETCH_NUM_DAYS = 30
