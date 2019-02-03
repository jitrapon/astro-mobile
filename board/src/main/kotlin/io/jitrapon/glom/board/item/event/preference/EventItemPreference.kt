package io.jitrapon.glom.board.item.event.preference

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.board.item.event.calendar.DeviceCalendar
import java.util.*

/**
 * Customization option preferences for event item list in a board
 *
 * Created by Jitrapon
 */
data class EventItemPreference(val calendarPreference: CalendarPreference,
                               override var retrievedTime: Date? = null,
                               override val error: Throwable? = null
) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readParcelable<CalendarPreference>(CalendarPreference::class.java.classLoader)!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(calendarPreference, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<EventItemPreference> {
        override fun createFromParcel(parcel: Parcel): EventItemPreference {
            return EventItemPreference(parcel)
        }

        override fun newArray(size: Int): Array<EventItemPreference?> {
            return arrayOfNulls(size)
        }
    }

}

/**
 * List of all device calendars that are both synced and unsynced to the board
 */
data class CalendarPreference(val calendars: List<DeviceCalendar>,
                              override var retrievedTime: Date? = null,
                              override val error: Throwable? = null
) : DataModel {

    constructor(parcel: Parcel) : this(parcel.createTypedArrayList(DeviceCalendar)!!)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(calendars)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CalendarPreference> {
        override fun createFromParcel(parcel: Parcel): CalendarPreference {
            return CalendarPreference(parcel)
        }

        override fun newArray(size: Int): Array<CalendarPreference?> {
            return arrayOfNulls(size)
        }
    }
}