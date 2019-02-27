package io.jitrapon.glom.board.item.event.calendar

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.CalendarContract
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.base.model.NoCalendarPermissionException
import io.jitrapon.glom.base.util.hasReadCalendarPermission
import io.jitrapon.glom.base.util.hasWriteCalendarPermission
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventInfo
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventLocation
import io.jitrapon.glom.board.item.event.EventSource
import io.jitrapon.glom.board.item.event.preference.CalendarPreference
import io.reactivex.Flowable
import java.util.*

// Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
private val CALENDAR_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID,                     // 0
    CalendarContract.Calendars.ACCOUNT_NAME,            // 1
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 2
    CalendarContract.Calendars.OWNER_ACCOUNT,           // 3
    CalendarContract.Calendars.CALENDAR_COLOR,          // 4
    CalendarContract.Calendars.VISIBLE,                 // 5
    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL    // 6
)

private val EVENT_CALENDAR_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Events._ID,                        // 0    The _ID of this event
    CalendarContract.Events.CALENDAR_ID,                // 1    The _ID of the calendar the event belongs to.
    CalendarContract.Events.ORGANIZER,                  // 2    Email of the organizer (owner) of the event.
    CalendarContract.Events.TITLE,                      // 3    The title of the event.
    CalendarContract.Events.EVENT_LOCATION,             // 4    Where the event takes place.
    CalendarContract.Events.DESCRIPTION,                // 5    The description of the event.
    CalendarContract.Events.DTSTART,                    // 6    The time the event starts in UTC milliseconds since the epoch.
    CalendarContract.Events.DTEND,                      // 7    The time the event ends in UTC milliseconds since the epoch.
    CalendarContract.Events.EVENT_TIMEZONE,             // 8    The time zone for the event.
    CalendarContract.Events.DURATION,                   // 9    The duration of the event in RFC5545 format.
                                                        //      For example, a value of "PT1H" states that the event
                                                        //      should last one hour, and a value of "P2W" indicates a duration of 2 weeks.
    CalendarContract.Events.ALL_DAY,                    // 10   A value of 1 indicates this event occupies the entire day, as defined by the local time zone.
                                                        //      A value of 0 indicates it is a regular event that may start and end at any time during a day.
    CalendarContract.Events.RRULE,                      // 11   The recurrence rule for the event format. For example, "FREQ=WEEKLY;COUNT=10;WKST=SU".
    CalendarContract.Events.RDATE,                      // 12   The recurrence dates for the event. You typically use RDATE in conjunction with RRULE
                                                        //      to define an aggregate set of repeating occurrences. For more discussion, see the RFC5545 spec.
    CalendarContract.Events.AVAILABILITY                // 13   If this event counts as busy time or is free time that can be scheduled over.
)

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX: Int = 1
private const val PROJECTION_DISPLAY_NAME_INDEX: Int = 2
private const val PROJECTION_OWNER_ACCOUNT_INDEX: Int = 3
private const val PROJECTION_CALENDAR_COLOR_INDEX: Int = 4
private const val PROJECTION_CALENDAR_VISIBLE_INDEX: Int = 5
private const val PROJECTION_CALENDAR_ACCESS_LEVEL: Int = 6

private const val PROJECTION_EVENT_ID = 0
private const val PROJECTION_EVENT_CALENDAR_ID = 1
private const val PROJECTION_EVENT_ORGANIZER = 2
private const val PROJECTION_EVENT_TITLE = 3
private const val PROJECTION_EVENT_LOCATION = 4
private const val PROJECTION_EVENT_DESCRIPTION = 5
private const val PROJECTION_EVENT_DTSTART = 6
private const val PROJECTION_EVENT_DTEND = 7
private const val PROJECTION_EVENT_TIMEZONE = 8
private const val PROJECTION_EVENT_DURATION = 9
private const val PROJECTION_EVENT_ALL_DAY = 10
private const val PROJECTION_EVENT_RRULE = 11
private const val PROJECTION_EVENT_RDATE = 12
private const val PROJECTION_EVENT_AVAILABILITY = 13

class CalendarDaoImpl(private val context: Context) :
    CalendarDao {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    /**
     * Whether or not a calendar is read-only
     */
    private fun isCalendarWritable(isVisible: Boolean, accessLevel: Int): Boolean =
        accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

    @SuppressLint("MissingPermission")
    @WorkerThread
    override fun getEventsSync(calendars: List<DeviceCalendar>, startSearchTime: Long, endSearchTime: Long?): List<EventItem> {
        return if (context.hasReadCalendarPermission() && context.hasWriteCalendarPermission()) {
            ArrayList<EventItem>().apply {
                val uri: Uri = CalendarContract.Events.CONTENT_URI
                var cur: Cursor? = null
                try {
                    val endSearchQuery = endSearchTime?.let { "AND ${CalendarContract.Events.DTSTART} <= $endSearchTime" }
                    val ids = StringBuffer().apply {
                        for (i in calendars.indices) {
                            append(calendars[i].calId)
                            if (i != calendars.size - 1) append(",")
                        }
                    }.toString()
                    cur = contentResolver.query(uri,
                        EVENT_CALENDAR_PROJECTION,
                        "${CalendarContract.Events.CALENDAR_ID} in ($ids) AND ${CalendarContract.Events.DTSTART} >= $startSearchTime $endSearchQuery",
                        null, null)
                    cur ?: return ArrayList()
                    val map = calendars.associateBy { it.calId }

                    while (cur.moveToNext()) {
                        add(EventItem(BoardItem.TYPE_EVENT,
                            cur.getLong(PROJECTION_EVENT_ID).toString(),
                            null, null,
                            cur.getStringOrNull(PROJECTION_EVENT_ORGANIZER)?.let(::listOf) ?: listOf(),
                            EventInfo(
                                cur.getStringOrNull(PROJECTION_EVENT_TITLE) ?: "",
                                cur.getLongOrNull(PROJECTION_EVENT_DTSTART),
                                cur.getLongOrNull(PROJECTION_EVENT_DTEND),
                                EventLocation(cur.getStringOrNull(PROJECTION_EVENT_LOCATION)),
                                cur.getStringOrNull(PROJECTION_EVENT_DESCRIPTION),
                                cur.getStringOrNull(PROJECTION_EVENT_TIMEZONE),
                                cur.getIntOrNull(PROJECTION_EVENT_ALL_DAY) == 1,
                                null, false, false,
                                arrayListOf(),
                                EventSource(null, map[cur.getLong(PROJECTION_EVENT_CALENDAR_ID)], null)
                            ), SyncStatus.OFFLINE, Date()
                        ))
                    }
                }
                catch (ex: Exception) {
                    throw ex
                }
                finally {
                    cur?.close()
                }
            }
        }
        else throw NoCalendarPermissionException()
    }

    @SuppressLint("MissingPermission")
    override fun getCalendars(calendarIds: List<String>): List<DeviceCalendar> {
        return if (calendarIds.isEmpty()) listOf()
        else {
            if (context.hasReadCalendarPermission() && context.hasWriteCalendarPermission()) {
                val uri: Uri = CalendarContract.Calendars.CONTENT_URI
                val result: MutableList<DeviceCalendar> = mutableListOf()
                var cur: Cursor? = null
                try {
                    val ids = StringBuffer().apply {
                        for (i in calendarIds.indices) {
                            append(calendarIds[i])
                            if (i != calendarIds.size - 1) append(",")
                        }
                    }.toString()
                    cur = contentResolver.query(uri,
                            CALENDAR_PROJECTION, "${CalendarContract.Calendars._ID} in ($ids)", null, null)
                    cur ?: return listOf()

                    while (cur.moveToNext()) {
                        val calId: Long = cur.getLong(PROJECTION_ID_INDEX)
                        val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                        val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                        val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                        val color: Int = cur.getInt(PROJECTION_CALENDAR_COLOR_INDEX)
                        val isVisible: Boolean = cur.getInt(PROJECTION_CALENDAR_VISIBLE_INDEX) == 1
                        val accessLevel: Int = cur.getInt(PROJECTION_CALENDAR_ACCESS_LEVEL)
                        result.add(
                                DeviceCalendar(
                                        calId,
                                        displayName,
                                        accountName,
                                        ownerName,
                                        color,
                                        isVisible,
                                        false,
                                        true,
                                        isCalendarWritable(isVisible, accessLevel)
                                )
                        )
                    }
                }
                catch (ex: Exception) {
                    throw ex
                }
                finally {
                    cur?.close()
                }
                result
            }
            else throw NoCalendarPermissionException()
        }
    }

    @SuppressLint("MissingPermission")
    override fun getCalendars(): Flowable<CalendarPreference> {
        return if (context.hasReadCalendarPermission() && context.hasWriteCalendarPermission()) {
            Flowable.fromCallable {
                val uri: Uri = CalendarContract.Calendars.CONTENT_URI
                val result: MutableList<DeviceCalendar> = mutableListOf()
                var exception: Exception? = null

                // to see all calendars that a user has viewed, not just calendars the user owns, omit the OWNER_ACCOUNT
                var cur: Cursor? = null
                try {
                    cur = contentResolver.query(uri,
                            CALENDAR_PROJECTION, null, null, null)
                    cur ?: return@fromCallable CalendarPreference(result, Date(), exception)

                    while (cur.moveToNext()) {
                        val calId: Long = cur.getLong(PROJECTION_ID_INDEX)
                        val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                        val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                        val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                        val color: Int = cur.getInt(PROJECTION_CALENDAR_COLOR_INDEX)
                        val isVisible: Boolean = cur.getInt(PROJECTION_CALENDAR_VISIBLE_INDEX) == 1
                        val accessLevel: Int = cur.getInt(PROJECTION_CALENDAR_ACCESS_LEVEL)
                        result.add(
                                DeviceCalendar(
                                        calId,
                                        displayName,
                                        accountName,
                                        ownerName,
                                        color,
                                        isVisible,
                                        false,
                                        true,
                                        isCalendarWritable(isVisible, accessLevel)
                                )
                        )
                    }
                }
                catch (ex: Exception) {
                    exception = ex
                }
                finally {
                    cur?.close()
                }
                CalendarPreference(result, Date(), exception)
            }
        }
        else Flowable.just(CalendarPreference(listOf(), Date(), NoCalendarPermissionException()))
    }
}

data class DeviceCalendar(val calId: Long, var displayName: String, var accountName: String,
                          var ownerName: String, @ColorInt var color: Int, var isVisible: Boolean,
                          var isSyncedToBoard: Boolean, var isLocal: Boolean, var isWritable: Boolean,
                          override var retrievedTime: Date? = null,
                          override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readLong(),
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readInt(),
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(calId)
        parcel.writeString(displayName)
        parcel.writeString(accountName)
        parcel.writeString(ownerName)
        parcel.writeInt(color)
        parcel.writeByte(if (isVisible) 1 else 0)
        parcel.writeByte(if (isSyncedToBoard) 1 else 0)
        parcel.writeByte(if (isLocal) 1 else 0)
        parcel.writeByte(if (isWritable) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DeviceCalendar> {
        override fun createFromParcel(parcel: Parcel): DeviceCalendar {
            return DeviceCalendar(parcel)
        }

        override fun newArray(size: Int): Array<DeviceCalendar?> {
            return arrayOfNulls(size)
        }
    }
}
