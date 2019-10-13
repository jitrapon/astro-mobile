package io.jitrapon.glom.board.item.event.calendar

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.os.Parcelable
import android.provider.CalendarContract
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.base.model.NoCalendarPermissionException
import io.jitrapon.glom.base.model.RecurringSaveOption
import io.jitrapon.glom.base.model.toRepeatInfo
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.addDay
import io.jitrapon.glom.base.util.addHour
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.util.hasReadCalendarPermission
import io.jitrapon.glom.base.util.hasWriteCalendarPermission
import io.jitrapon.glom.base.util.toDurationString
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.event.EventInfo
import io.jitrapon.glom.board.item.event.EventItem
import io.jitrapon.glom.board.item.event.EventLocation
import io.jitrapon.glom.board.item.event.EventSource
import io.jitrapon.glom.board.item.event.preference.CALENDAR_OBSERVER_USE_WORKER
import io.jitrapon.glom.board.item.event.preference.CalendarPreference
import io.reactivex.Flowable
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

// Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
private val CALENDAR_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID,                     // 0
    CalendarContract.Calendars.ACCOUNT_NAME,            // 1
    CalendarContract.Calendars.ACCOUNT_TYPE,            // 2
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 3
    CalendarContract.Calendars.OWNER_ACCOUNT,           // 4
    CalendarContract.Calendars.CALENDAR_COLOR,          // 5
    CalendarContract.Calendars.VISIBLE,                 // 6
    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL    // 7
)

private val EVENT_PROJECTION: Array<String> = arrayOf(
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

private val EVENT_INSTANCE_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Instances._ID,                     // 0 The _ID of this occurrence
    CalendarContract.Instances.EVENT_ID,                // 1 The foreign key to the Events table
    CalendarContract.Instances.BEGIN,                   // 2 The beginning time of the instance, in UTC milliseconds.
    CalendarContract.Instances.END,                     // 3 The ending time of the instance, in UTC milliseconds.
    CalendarContract.Instances.CALENDAR_ID,
    CalendarContract.Instances.RRULE,
    CalendarContract.Instances.RDATE,
    CalendarContract.Instances.EXDATE,
    CalendarContract.Instances.ORGANIZER,
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.EVENT_LOCATION,
    CalendarContract.Instances.DESCRIPTION,
    CalendarContract.Instances.EVENT_TIMEZONE,
    CalendarContract.Instances.ALL_DAY
)

private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX: Int = 1
private const val PROJECTION_ACCOUNT_TYPE_INDEX: Int = 2
private const val PROJECTION_DISPLAY_NAME_INDEX: Int = 3
private const val PROJECTION_OWNER_ACCOUNT_INDEX: Int = 4
private const val PROJECTION_CALENDAR_COLOR_INDEX: Int = 5
private const val PROJECTION_CALENDAR_VISIBLE_INDEX: Int = 6
private const val PROJECTION_CALENDAR_ACCESS_LEVEL: Int = 7

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

private const val PROJECTION_INSTANCE_ID = 0
private const val PROJECTION_INSTANCE_EVENT_ID = 1
private const val PROJECTION_INSTANCE_BEGIN = 2
private const val PROJECTION_INSTANCE_END = 3
private const val PROJECTION_INSTANCE_CALENDAR = 4
private const val PROJECTION_INSTANCE_RRULE = 5
private const val PROJECTION_INSTANCE_RDATE = 6
private const val PROJECTION_INSTANCE_EXDATE = 7
private const val PROJECTION_INSTANCE_ORGANIZER = 8
private const val PROJECTION_INSTANCE_TITLE = 9
private const val PROJECTION_INSTANCE_LOCATION = 10
private const val PROJECTION_INSTANCE_DESCRIPTION = 11
private const val PROJECTION_INSTANCE_TIMEZONE = 12
private const val PROJECTION_INSTANCE_ALL_DAY = 13

class CalendarDaoImpl(private val context: Context) :
    CalendarDao {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    private val handlerThread: HandlerThread by lazy {
        HandlerThread("CalendarDaoImplHandlerThread").apply {
            start()
        }
    }

    private val handler: Handler = Handler(handlerThread.looper)

    private var contentChangeListener: ((Boolean) -> Unit)? = null

    private var isSelfModified = AtomicBoolean(false)

    /**
     * Whether or not a calendar is read-only
     */
    private fun isCalendarWritable(isVisible: Boolean, accessLevel: Int): Boolean = isVisible &&
            accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

    @SuppressLint("MissingPermission")
    override fun getEventsSync(
        calendars: List<DeviceCalendar>,
        startSearchTime: Long,
        endSearchTime: Long?,
        requestSync: Boolean
    ): List<EventItem> {
        return if (context.hasReadCalendarPermission() && context.hasWriteCalendarPermission()) {
            val calendarMap = calendars.associateBy { it.calId }
            val calendarIds = StringBuffer().apply {
                for (i in calendars.indices) {
                    append(calendars[i].calId)
                    if (i != calendars.size - 1) append(",")
                }
            }.toString()

            if (requestSync) {
                for (calendar in calendars) {
                    syncCalendar(calendar)
                }
            }

            ArrayList<EventItem>().apply {
                addNonRecurringEvents(
                    this,
                    startSearchTime,
                    endSearchTime,
                    calendarIds,
                    calendarMap
                )
                addRecurringEvents(this, startSearchTime, endSearchTime, calendarIds, calendarMap)
            }
        }
        else throw NoCalendarPermissionException()
    }

    @WorkerThread
    override fun syncCalendar(calendar: DeviceCalendar) {
        if (calendar.isWritable) {
            AppLogger.d("Attempting to sync calendar $calendar")
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.VISIBLE, 1)
            }
            isSelfModified.set(true)
            contentResolver.update(
                ContentUris.withAppendedId(
                    CalendarContract.Calendars.CONTENT_URI,
                    calendar.calId
                ), values, null, null
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun addNonRecurringEvents(
        events: ArrayList<EventItem>,
        startSearchTime: Long,
        endSearchTime: Long?,
        calendarIds: String,
        calendarMap: Map<Long, DeviceCalendar>
    ) {
        var cur: Cursor? = null
        val endSearchQuery =
            endSearchTime?.let { "AND ${CalendarContract.Events.DTSTART} <= $endSearchTime" }

        try {
            cur = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                EVENT_PROJECTION,
                "${CalendarContract.Events.CALENDAR_ID} in ($calendarIds) " +
                        "AND ${CalendarContract.Events.DTSTART} >= $startSearchTime $endSearchQuery " +
                        "AND ${CalendarContract.Events.DELETED} = 0 " +
                        "AND ${CalendarContract.Events.RRULE} IS NULL " +
                        "AND ${CalendarContract.Events.STATUS} < ${CalendarContract.Events.STATUS_CANCELED}",
                null, null
            )
            if (cur == null || cur.count == 0) return
            while (cur.moveToNext()) {
                val calendar = calendarMap[cur.getLong(PROJECTION_EVENT_CALENDAR_ID)]
                events.add(
                    EventItem(
                        BoardItem.TYPE_EVENT,
                        cur.getLong(PROJECTION_EVENT_ID).toString(),
                        null, null,
                        cur.getStringOrNull(PROJECTION_EVENT_ORGANIZER)?.let(::listOf)
                            ?: listOf(),
                        EventInfo(
                            cur.getStringOrNull(PROJECTION_EVENT_TITLE) ?: "",
                            cur.getLongOrNull(PROJECTION_EVENT_DTSTART),
                            cur.getLongOrNull(PROJECTION_EVENT_DTEND),
                            EventLocation(cur.getStringOrNull(PROJECTION_EVENT_LOCATION)),
                            cur.getStringOrNull(PROJECTION_EVENT_DESCRIPTION),
                            cur.getStringOrNull(PROJECTION_EVENT_TIMEZONE),
                            cur.getIntOrNull(PROJECTION_EVENT_ALL_DAY) == 1,
                            null,
                            datePollStatus = false,
                            placePollStatus = false,
                            attendees = arrayListOf(),
                            source = EventSource(
                                null,
                                calendarMap[cur.getLong(PROJECTION_EVENT_CALENDAR_ID)],
                                null,
                                null
                            )
                        ), calendar?.isWritable ?: true, SyncStatus.SUCCESS, Date()
                    )
                )
            }
        }
        catch (ex: Exception) {
            AppLogger.e(ex)
            throw ex
        }
        finally {
            cur?.close()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addRecurringEvents(
        events: ArrayList<EventItem>,
        startSearchTime: Long,
        endSearchTime: Long?,
        calendarIds: String,
        calendarMap: Map<Long, DeviceCalendar>
    ) {
        var instanceCur: Cursor? = null
        var eventCur: Cursor? = null

        try {
            instanceCur = contentResolver.query(
                CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                    it.appendPath("$startSearchTime")
                    it.appendPath(
                        "${endSearchTime ?: Date(startSearchTime).addDay(
                            30
                        ).time}"
                    )
                    it.build()
                },
                EVENT_INSTANCE_PROJECTION,
                "${CalendarContract.Instances.RRULE} IS NOT NULL " +
                        "AND ${CalendarContract.Instances.CALENDAR_ID} in ($calendarIds)",
                null, null
            )
            if (instanceCur != null && instanceCur.count > 0) {
                while (instanceCur.moveToNext()) {
                    val calendar = calendarMap[instanceCur.getLong(PROJECTION_INSTANCE_CALENDAR)]
                    val rrule = instanceCur.getStringOrNull(PROJECTION_INSTANCE_RRULE)
                    val rdate = instanceCur.getStringOrNull(PROJECTION_INSTANCE_RDATE)
                    val exdate = instanceCur.getStringOrNull(PROJECTION_INSTANCE_EXDATE)
                    val eventId = instanceCur.getStringOrNull(PROJECTION_INSTANCE_EVENT_ID)
                    val eventInstanceId = instanceCur.getLongOrNull(PROJECTION_INSTANCE_ID)

                    // query some necessary information from the parent EVENTS table that
                    // are not in the INSTANCE table
                    var firstOccurrenceStartTime: Long? = null
                    var isDeleted = false
                    var syncId: String? = null
                    eventCur = contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Events.DTSTART,
                            CalendarContract.Events.DELETED,
                            CalendarContract.Events._SYNC_ID
                        ),
                        "${CalendarContract.Events.DELETED} = 0 " +
                                "AND ${CalendarContract.Events._ID} = $eventId",
                        null, null
                    )
                    if (eventCur != null && eventCur.moveToNext()) {
                        firstOccurrenceStartTime = eventCur.getLongOrNull(0)
                        isDeleted = eventCur.getIntOrNull(1) == 1
                        syncId = eventCur.getStringOrNull(2)
                    }

                    AppLogger.d(
                        "Recurring event eventId=$eventId, syncId=$syncId, " +
                                "instanceId=$eventInstanceId, rrule=$rrule, " +
                                "rdate=$rdate, exdate=$exdate, firstOccurrence=${Date(
                                    firstOccurrenceStartTime!!
                                )}"
                    )

                    if (!isDeleted) {
                        events.add(
                            EventItem(
                                BoardItem.TYPE_EVENT,
                                "$eventId.$eventInstanceId",
                                null,
                                null,
                                instanceCur.getStringOrNull(PROJECTION_INSTANCE_ORGANIZER)?.let(::listOf)
                                    ?: listOf(),
                                EventInfo(
                                    "${instanceCur.getStringOrNull(PROJECTION_INSTANCE_TITLE)}",
                                    instanceCur.getLongOrNull(PROJECTION_INSTANCE_BEGIN),
                                    instanceCur.getLongOrNull(PROJECTION_INSTANCE_END),
                                    EventLocation(
                                        instanceCur.getStringOrNull(
                                            PROJECTION_INSTANCE_LOCATION
                                        )
                                    ),
                                    instanceCur.getStringOrNull(PROJECTION_INSTANCE_DESCRIPTION),
                                    instanceCur.getStringOrNull(PROJECTION_INSTANCE_TIMEZONE),
                                    instanceCur.getIntOrNull(PROJECTION_INSTANCE_ALL_DAY) == 1,
                                    rrule.toRepeatInfo(
                                        eventInstanceId,
                                        false,
                                        firstOccurrenceStartTime ?: 0L,
                                        instanceCur.getLongOrNull(PROJECTION_INSTANCE_BEGIN),
                                        instanceCur.getIntOrNull(PROJECTION_INSTANCE_ALL_DAY) == 1,
                                        syncId
                                    ),
                                    datePollStatus = false,
                                    placePollStatus = false,
                                    attendees = arrayListOf(),
                                    source = EventSource(
                                        null,
                                        calendar,
                                        null,
                                        null
                                    )
                                ),
                                calendar?.isWritable ?: true,
                                SyncStatus.SUCCESS,
                                Date()
                            )
                        )
                    }
                }
            }
        }
        catch (ex: Exception) {
            throw ex
        }
        finally {
            instanceCur?.close()
            eventCur?.close()
        }
    }

    @SuppressLint("MissingPermission")
    override fun createEvent(event: EventItem, calendar: DeviceCalendar): Boolean {
        val currentTimeInMs = "${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.itemInfo.eventName)
            put(CalendarContract.Events.ORGANIZER, event.owners.get(0, null))
            put(CalendarContract.Events.EVENT_LOCATION, event.itemInfo.location?.name)
            put(CalendarContract.Events.DESCRIPTION, event.itemInfo.note)
            put(CalendarContract.Events.DTSTART, event.itemInfo.startTime)
            if (event.itemInfo.repeatInfo == null) {
                put(CalendarContract.Events.DTEND, event.itemInfo.endTime)
            }
            else {
                //must include duration if event is recurring
                val duration = event.itemInfo.startTime?.let {
                    val endTime = event.itemInfo.endTime ?: Date(it).addHour(1).time
                    endTime - it
                } ?: throw Exception("Cannot create a recurring event without start time")
                put(CalendarContract.Events.DURATION, duration.toDurationString())
            }
            put(CalendarContract.Events.EVENT_TIMEZONE, event.itemInfo.timeZone)
            put(CalendarContract.Events.ALL_DAY, if (event.itemInfo.isFullDay) 1 else 0)
            put(CalendarContract.Events.RRULE, event.itemInfo.repeatInfo?.rrule)
            put(CalendarContract.Events.CALENDAR_ID, calendar.calId)
        }
        isSelfModified.set(true)
        event.itemId =
            contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment
                ?: currentTimeInMs
        return event.itemInfo.repeatInfo != null
    }

    @SuppressLint("MissingPermission")
    override fun updateEvent(event: EventItem, calendar: DeviceCalendar?): Boolean {
        ContentValues().apply {
            put(CalendarContract.Events.TITLE, event.itemInfo.eventName)
            put(CalendarContract.Events.ORGANIZER, event.owners.get(0, null))
            put(CalendarContract.Events.EVENT_LOCATION, event.itemInfo.location?.name)
            put(CalendarContract.Events.DESCRIPTION, event.itemInfo.note)
            put(CalendarContract.Events.DTSTART, event.itemInfo.startTime)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.itemInfo.timeZone)
            put(CalendarContract.Events.ALL_DAY, if (event.itemInfo.isFullDay) 1 else 0)

            isSelfModified.set(true)
            if (event.itemInfo.repeatInfo == null) {
                createNonRecurringUpdateContentUri(event, calendar)
            }
            else {
                createRecurringUpdateContentUri(event, calendar)
            }
        }
        return event.itemInfo.repeatInfo != null
    }

    private fun ContentValues.createNonRecurringUpdateContentUri(
        event: EventItem,
        calendar: DeviceCalendar?
    ) {
        val duration: Long? = null
        val rrule: String? = null
        put(CalendarContract.Events.DTEND, event.itemInfo.endTime)
        put(CalendarContract.Events.DURATION, duration)
        put(CalendarContract.Events.RRULE, rrule)
        calendar?.calId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }

        val updateUri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            event.itemId.toLong()
        )
        contentResolver.update(updateUri, this, null, null)
    }

    @SuppressLint("MissingPermission")
    private fun ContentValues.createRecurringUpdateContentUri(
        event: EventItem,
        calendar: DeviceCalendar?
    ) {
        // case 1: event is repeating and is rescheduled
        // create a new event exception
        val eventId = event.instanceEventId
        if (event.itemInfo.repeatInfo?.isReschedule == true) {

            // check the editMode whether we should update only an instance of this recurring event,
            // or update all instances
            val editMode = event.itemInfo.repeatInfo?.editMode
            event.itemInfo.repeatInfo?.editMode = null
            if (editMode == RecurringSaveOption.ALL) {
                // must find the original event in the Events table
                // then modify its recurrence info and start and end dates
                val originalStartDateTimeCalendar =
                    event.itemInfo.repeatInfo?.firstInstanceStartTime!!.let {
                        Calendar.getInstance().apply {
                            timeInMillis = it
                        }
                    }
                val dtend: Long? = null
                val newStartTime = event.itemInfo.startTime?.let {
                    Calendar.getInstance().apply {
                        time = Date(it)
                        set(
                            Calendar.WEEK_OF_YEAR,
                            originalStartDateTimeCalendar[Calendar.WEEK_OF_YEAR]
                        )
                        set(Calendar.MONTH, originalStartDateTimeCalendar[Calendar.MONTH])
                        set(Calendar.YEAR, originalStartDateTimeCalendar[Calendar.YEAR])
                        time
                    }.timeInMillis
                } ?: throw Exception("Cannot update event without start time")

                put(CalendarContract.Events.DTSTART, newStartTime)
                put(CalendarContract.Events.DTEND, dtend)
                val duration = event.itemInfo.startTime?.let {
                    val endTime = event.itemInfo.endTime ?: Date(it).addHour(1).time
                    endTime - it
                } ?: throw Exception("Cannot update event without start time")
                put(CalendarContract.Events.DURATION, duration.toDurationString())
                put(CalendarContract.Events.RRULE, event.itemInfo.repeatInfo?.rrule)
                calendar?.calId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }

                val updateUri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId.toLong()
                )
                contentResolver.update(updateUri, this, null, null)
            }
            else if (editMode == RecurringSaveOption.SINGLE) {
//            put(CalendarContract.Events.DTEND, event.itemInfo.endTime)
//            put(CalendarContract.Events.ORIGINAL_ID, eventId)
//            put(
//                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
//                event.itemInfo.repeatInfo?.instanceStartTime
//            )
//            put(
//                CalendarContract.Events.ORIGINAL_ALL_DAY,
//                if (event.itemInfo.repeatInfo?.instanceIsFullDay == true) 1 else 0
//            )
//
//            contentResolver.insert(CalendarContract.Events.CONTENT_URI, this)
                put(
                    CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                    event.itemInfo.repeatInfo?.instanceStartTime
                )
                val duration = event.itemInfo.startTime?.let {
                    val endTime = event.itemInfo.endTime ?: Date(it).addHour(1).time
                    endTime - it
                } ?: throw Exception("Cannot update event without start time")
                put(CalendarContract.Events.DURATION, duration.toDurationString())
                calendar?.let {
                    put(CalendarContract.Events.CALENDAR_ID, it.calId)
                }
                val exceptionUri = Uri.withAppendedPath(
                    CalendarContract.Events.CONTENT_EXCEPTION_URI,
                    eventId
                )
                contentResolver.insert(exceptionUri, this)
            }

            // need to trigger a calendar sync so that recurring instances are re-generated correctly
            // in time
            (calendar ?: event.itemInfo.source.calendar)?.let {
                syncCalendar(it)
            }
        }

        // case 2: event was non-repeating, but is changed repeating
        else {
            //must include duration if event is recurring
            val time: Long? = null
            val duration = event.itemInfo.startTime?.let {
                val endTime = event.itemInfo.endTime ?: Date(it).addHour(1).time
                endTime - it
            } ?: throw Exception("Cannot create a recurring event without start time")
            put(CalendarContract.Events.RRULE, event.itemInfo.repeatInfo?.rrule)
            put(CalendarContract.Events.DTEND, time)
            put(CalendarContract.Events.DURATION, duration.toDurationString())
            calendar?.let {
                put(CalendarContract.Events.CALENDAR_ID, it.calId)
            }
            val updateUri = ContentUris.withAppendedId(
                CalendarContract.Events.CONTENT_URI,
                event.itemId.toLong()
            )
            contentResolver.update(updateUri, this, null, null)
        }
    }

    override fun deleteEvent(event: EventItem): Boolean {
        val editMode = event.itemInfo.repeatInfo?.editMode
        event.itemInfo.repeatInfo?.editMode = null
        when (editMode) {
            RecurringSaveOption.SINGLE -> {
                val values = ContentValues().apply {
                    put(
                        CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                        event.itemInfo.repeatInfo?.instanceStartTime
                    )
                    val duration = event.itemInfo.startTime?.let {
                        val endTime = event.itemInfo.endTime ?: Date(it).addHour(1).time
                        endTime - it
                    } ?: throw Exception("Cannot update event without start time")
                    put(CalendarContract.Events.DURATION, duration.toDurationString())
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                val exceptionUri = Uri.withAppendedPath(
                    CalendarContract.Events.CONTENT_EXCEPTION_URI,
                    event.instanceEventId
                )
                contentResolver.insert(exceptionUri, values)
            }
            else -> {
                val eventId =
                    if (event.itemInfo.repeatInfo != null) event.instanceEventId.toLong() else event.itemId.toLong()
                val deleteUri =
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI,
                        eventId
                    )
                contentResolver.delete(deleteUri, null, null)
            }
        }
        isSelfModified.set(true)
        return event.itemInfo.repeatInfo != null && editMode != RecurringSaveOption.SINGLE
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
                    cur = contentResolver.query(
                        uri,
                        CALENDAR_PROJECTION,
                        "${CalendarContract.Calendars._ID} in ($ids)",
                        null,
                        null
                    )
                    cur ?: return listOf()

                    while (cur.moveToNext()) {
                        val calId: Long = cur.getLong(PROJECTION_ID_INDEX)
                        val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                        val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                        val accountType: String = cur.getString(PROJECTION_ACCOUNT_TYPE_INDEX)
                        val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                        val color: Int = cur.getInt(PROJECTION_CALENDAR_COLOR_INDEX)
                        val isVisible: Boolean =
                            cur.getInt(PROJECTION_CALENDAR_VISIBLE_INDEX) == 1
                        val accessLevel: Int = cur.getInt(PROJECTION_CALENDAR_ACCESS_LEVEL)
                        result.add(
                            DeviceCalendar(
                                calId,
                                displayName,
                                accountName,
                                accountType,
                                ownerName,
                                color,
                                isVisible,
                                isSyncedToBoard = false,
                                isLocal = true,
                                isWritable = isCalendarWritable(isVisible, accessLevel)
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
                    cur = contentResolver.query(
                        uri,
                        CALENDAR_PROJECTION, null, null, null
                    )
                    cur ?: return@fromCallable CalendarPreference(result, Date(), exception)

                    while (cur.moveToNext()) {
                        val calId: Long = cur.getLong(PROJECTION_ID_INDEX)
                        val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                        val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                        val accountType: String = cur.getString(PROJECTION_ACCOUNT_TYPE_INDEX)
                        val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                        val color: Int = cur.getInt(PROJECTION_CALENDAR_COLOR_INDEX)
                        val isVisible: Boolean =
                            cur.getInt(PROJECTION_CALENDAR_VISIBLE_INDEX) == 1
                        val accessLevel: Int = cur.getInt(PROJECTION_CALENDAR_ACCESS_LEVEL)
                        result.add(
                            DeviceCalendar(
                                calId,
                                displayName,
                                accountName,
                                accountType,
                                ownerName,
                                color,
                                isVisible,
                                isSyncedToBoard = false,
                                isLocal = true,
                                isWritable = isCalendarWritable(isVisible, accessLevel)
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
        else Flowable.just(
            CalendarPreference(
                listOf(),
                Date(),
                NoCalendarPermissionException()
            )
        )
    }

    private val calendarReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            AppLogger.d("CalendarReceiver receives calendar update event")
            if (!isSelfModified.getAndSet(false)) {
                contentChangeListener?.invoke(true)
            }
        }
    }

    private fun startObservingCalendarChange() {
        if (CALENDAR_OBSERVER_USE_WORKER) {
            SyncWorker.schedule(context)
            IntentFilter().apply {
                addAction(ACTION_CALENDAR_MODIFIED)
                LocalBroadcastManager.getInstance(context).registerReceiver(calendarReceiver, this)
            }
        }
        else {
            IntentFilter().apply {
                addAction(Intent.ACTION_PROVIDER_CHANGED)
                addDataScheme("content")
                addDataAuthority("com.android.calendar", null)
                context.registerReceiver(calendarReceiver, this, null, handler)
            }
        }
    }

    private fun stopObservingCalendarChange() {
        if (CALENDAR_OBSERVER_USE_WORKER) {
            SyncWorker.unschedule(context)
            LocalBroadcastManager.getInstance(context).unregisterReceiver(calendarReceiver)
        }
        else {
            context.unregisterReceiver(calendarReceiver)
        }
    }

    override fun registerUpdateObserver(onContentChange: (selfChange: Boolean) -> Unit) {
        if (contentChangeListener != null) return

        contentChangeListener = onContentChange
        startObservingCalendarChange()
    }

    override fun unregisterUpdateObserver() {
        stopObservingCalendarChange()
    }
}

data class DeviceCalendar(
    val calId: Long, var displayName: String, var accountName: String, var accountType: String?,
    var ownerName: String, @ColorInt var color: Int, var isVisible: Boolean,
    var isSyncedToBoard: Boolean, var isLocal: Boolean, var isWritable: Boolean,
    override var retrievedTime: Date? = null,
    override val error: Throwable? = null
) : DataModel {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(calId)
        parcel.writeString(displayName)
        parcel.writeString(accountName)
        parcel.writeString(accountType)
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
