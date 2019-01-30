package io.jitrapon.glom.board.item.event.calendar

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.CalendarContract
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.hasReadCalendarPermission
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

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_ACCOUNT_NAME_INDEX: Int = 1
private const val PROJECTION_DISPLAY_NAME_INDEX: Int = 2
private const val PROJECTION_OWNER_ACCOUNT_INDEX: Int = 3
private const val PROJECTION_CALENDAR_COLOR_INDEX: Int = 4
private const val PROJECTION_CALENDAR_VISIBLE_INDEX: Int = 5

class CalendarDaoImpl(private val context: Context) :
    CalendarDao {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override fun getEvents(): Flowable<List<DeviceEvent>> {
        // make sure we have enough permissions
        return if (context.hasReadCalendarPermission()) {
            getCalendars().map {
                it.forEach { entity ->
                    AppLogger.d("Calendar: $entity")
                }
                val result: List<DeviceEvent> = emptyList()
                result
            }
        }
        else Flowable.error(IllegalAccessException("No READ_CALENDAR permission"))
    }

    @SuppressLint("MissingPermission")
    override fun getCalendars(): Flowable<List<DeviceCalendar>> {
        return Flowable.fromCallable {
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val result: MutableList<DeviceCalendar> = mutableListOf()

            // to see all calendars that a user has viewed, not just calendars the user owns, omit the OWNER_ACCOUNT
            var cur: Cursor? = null
            try {
                cur = contentResolver.query(uri,
                    EVENT_PROJECTION, null, null, null)
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
                throw ex
            }
            finally {
                cur?.close()
            }
            result
        }
    }
}

data class DeviceCalendar(val calId: Long, val displayName: String, val accountName: String,
                          val ownerName: String, val color: Int, val isVisible: Boolean,
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

data class DeviceEvent(val eventId: String,
                       override var retrievedTime: Date? = null,
                       override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readString()!!)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(eventId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DeviceEvent> {
        override fun createFromParcel(parcel: Parcel): DeviceEvent {
            return DeviceEvent(parcel)
        }

        override fun newArray(size: Int): Array<DeviceEvent?> {
            return arrayOfNulls(size)
        }
    }
}
