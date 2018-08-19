package io.jitrapon.glom.board.event

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */
data class EventLocation(val latitude: Double?,
                         val longitude: Double?,
                         val googlePlaceId: String?,
                         val placeId: String?,
                         val name: String? = null,
                         val description: String? = null,
                         val address: String? = null,
                         override var retrievedTime: Date? = null,
                         override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readDouble().let {
                if (it == -1.0) null else it
            },
            parcel.readDouble().let {
                if (it == -1.0) null else it
            },
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString())

    constructor(name: String?) : this(null, null, null, null, name, null)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude ?: -1.0)
        parcel.writeDouble(longitude ?: -1.0)
        parcel.writeString(googlePlaceId)
        parcel.writeString(placeId)
        parcel.writeString(name)
        parcel.writeString(description)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EventLocation> {
        override fun createFromParcel(parcel: Parcel): EventLocation = EventLocation(parcel)

        override fun newArray(size: Int): Array<EventLocation?> = arrayOfNulls(size)
    }
}