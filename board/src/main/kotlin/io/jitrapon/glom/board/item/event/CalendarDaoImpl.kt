package io.jitrapon.glom.board.item.event

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import io.jitrapon.glom.base.util.hasReadCalendarPermission
import io.reactivex.Flowable

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

class CalendarDaoImpl(private val context: Context) : CalendarDao {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override fun getEvents(): Flowable<List<CalendarEntity>> {
        // make sure we have enough permissions
        if (context.hasReadCalendarPermission()) {
            return getCalendars()
        }
        else throw IllegalAccessException("No READ_CALENDAR permission")
    }

    @SuppressLint("MissingPermission")
    override fun getCalendars(): Flowable<List<CalendarEntity>> {
        return Flowable.fromCallable {
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val result: MutableList<CalendarEntity> = mutableListOf()

            // to see all calendars that a user has viewed, not just calendars the user owns, omit the OWNER_ACCOUNT
            var cur: Cursor? = null
            try {
                cur = contentResolver.query(uri, EVENT_PROJECTION, null, null, null)
                while (cur.moveToNext()) {
                    val calId: Long = cur.getLong(PROJECTION_ID_INDEX)
                    val displayName: String = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
                    val accountName: String = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
                    val ownerName: String = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)
                    val color: Int = cur.getInt(PROJECTION_CALENDAR_COLOR_INDEX)
                    val isVisible: Boolean = cur.getInt(PROJECTION_CALENDAR_VISIBLE_INDEX) == 1
                    result.add(CalendarEntity(calId, displayName, accountName, ownerName, color, isVisible))
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

data class CalendarEntity(val calId: Long, val displayName: String, val accountName: String,
                          val ownerName: String, val color: Int, val isVisible: Boolean)
