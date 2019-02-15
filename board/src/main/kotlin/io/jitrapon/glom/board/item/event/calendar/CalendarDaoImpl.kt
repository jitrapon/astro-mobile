package io.jitrapon.glom.board.item.event.calendar

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.CalendarContract
import androidx.annotation.WorkerThread
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.base.model.NoCalendarPermissionException
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.hasReadCalendarPermission
import io.jitrapon.glom.base.util.hasWriteCalendarPermission
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventInfo
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventLocation
import io.jitrapon.glom.board.item.event.preference.CalendarPreference
import io.reactivex.Flowable
import java.util.*

// Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
private val EVENT_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID,                     // 0
    CalendarContract.Calendars.ACCOUNT_NAME,            // 1
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 2
    CalendarContract.Calendars.OWNER_ACCOUNT,           // 3
    CalendarContract.Calendars.CALENDAR_COLOR,          // 4
    CalendarContract.Calendars.VISIBLE                  // 5
)

private val EVENT_INSTANCE_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Events._ID,                        // 0    The _ID of this event
    CalendarContract.Events.CALENDAR_ID,                // 1    The _ID of the calendar the event belongs to.
    CalendarContract.Events.ORGANIZER,                  // 2    Email of the organizer (owner) of the event.
    CalendarContract.Events.TITLE,                      // 3    The title of the event.
    CalendarContract.Events.EVENT_LOCATION,             // 4    Where the event takes place.
    CalendarContract.Events.DESCRIPTION,                // 5    The description of the event.
    CalendarContract.Events.DTSTART,                    // 6    The time the event starts in UTC milliseconds since the epoch.
    CalendarContract.Events.DTEND,                      // 7    The time the event ends in UTC milliseconds since the epoch.
    CalendarContract.Events.EVENT_TIMEZONE,             // 8    The time zone for the event.
    CalendarContract.Events.EVENT_END_TIMEZONE,         // 9    The time zone for the end time of the event.
    CalendarContract.Events.DURATION,                   // 10   The duration of the event in RFC5545 format.
                                                        //      For example, a value of "PT1H" states that the event
                                                        //      should last one hour, and a value of "P2W" indicates a duration of 2 weeks.
    CalendarContract.Events.ALL_DAY,                    // 11   A value of 1 indicates this event occupies the entire day, as defined by the local time zone.
                                                        //      A value of 0 indicates it is a regular event that may start and end at any time during a day.
    CalendarContract.Events.RRULE,                      // 12   The recurrence rule for the event format. For example, "FREQ=WEEKLY;COUNT=10;WKST=SU".
    CalendarContract.Events.RDATE,                      // 13   The recurrence dates for the event. You typically use RDATE in conjunction with RRULE
                                                        //      to define an aggregate set of repeating occurrences. For more discussion, see the RFC5545 spec.
    CalendarContract.Events.AVAILABILITY,               // 14   If this event counts as busy time or is free time that can be scheduled over.
    CalendarContract.Events.GUESTS_CAN_MODIFY,          // 15   Whether guests can modify the event.
    CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS,   // 16   Whether guests can invite other guests.
    CalendarContract.Events.GUESTS_CAN_SEE_GUESTS       // 17   Whether guests can see the list of attendees.
)

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX: Int = 1
private const val PROJECTION_DISPLAY_NAME_INDEX: Int = 2
private const val PROJECTION_OWNER_ACCOUNT_INDEX: Int = 3
private const val PROJECTION_CALENDAR_COLOR_INDEX: Int = 4
private const val PROJECTION_CALENDAR_VISIBLE_INDEX: Int = 5

private const val PROJECTION_EVENT_ID = 0
private const val PROJECTION_EVENT_CALENDAR_ID = 1
private const val PROJECTION_EVENT_ORGANIZER = 2
private const val PROJECTION_EVENT_TITLE = 3
private const val PROJECTION_EVENT_LOCATION = 4
private const val PROJECTION_EVENT_DESCRIPTION = 5
private const val PROJECTION_EVENT_DTSTART = 6
private const val PROJECTION_EVENT_DTEND = 7
private const val PROJECTION_EVENT_TIMEZONE = 8
private const val PROJECTION_EVENT_END_TIMEZONE = 9
private const val PROJECTION_EVENT_DURATION = 10
private const val PROJECTION_EVENT_ALL_DAY = 11
private const val PROJECTION_EVENT_RRULE = 12
private const val PROJECTION_EVENT_RDATE = 13
private const val PROJECTION_EVENT_AVAILABILITY = 14
private const val PROJECTION_EVENT_GUESTS_CAN_MODIFY = 15
private const val PROJECTION_EVENT_GUESTS_CAN_INVITE_OTHERS = 16
private const val PROJECTION_EVENT_GUESTS_CAN_SEE_GUESTS = 17

class CalendarDaoImpl(private val context: Context) :
    CalendarDao {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    @SuppressLint("MissingPermission")
    @WorkerThread
    override fun getEventsSync(calId: String, startSearchTime: Long, endSearchTime: Long?): List<EventItem> {
        return if (context.hasReadCalendarPermission() && context.hasWriteCalendarPermission()) {
            ArrayList<EventItem>().apply {
                val uri: Uri = CalendarContract.Events.CONTENT_URI
                var cur: Cursor? = null
                try {
                    val endSearchQuery = endSearchTime?.let { "AND ${CalendarContract.Events.DTSTART} <= $endSearchTime" }
                    cur = contentResolver.query(uri,
                        EVENT_INSTANCE_PROJECTION,
                        "${CalendarContract.Events.CALENDAR_ID} = '$calId' AND ${CalendarContract.Events.DTSTART} >= $startSearchTime $endSearchQuery",
                        null,
                        "${CalendarContract.Events.DTSTART} ASC")
                    cur ?: return ArrayList()

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
                                arrayListOf()
                            ), SyncStatus.OFFLINE, true
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
                            EVENT_PROJECTION, null, null, null)
                    cur ?: return@fromCallable CalendarPreference(result, Date(), exception)

                    while (cur.moveToNext()) {
                        val calId: Long = cur.getLong(PROJECTION_ID_INDEX)
                        val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                        val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                        val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                        val color: Int = cur.getInt(PROJECTION_CALENDAR_COLOR_INDEX)
                        val isVisible: Boolean = cur.getInt(PROJECTION_CALENDAR_VISIBLE_INDEX) == 1
                        result.add(
                                DeviceCalendar(
                                        calId,
                                        displayName,
                                        accountName,
                                        ownerName,
                                        color,
                                        isVisible,
                                        false,
                                        true
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
                          var ownerName: String, var color: Int, var isVisible: Boolean,
                          var isSyncedToBoard: Boolean, var isLocal: Boolean,
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
