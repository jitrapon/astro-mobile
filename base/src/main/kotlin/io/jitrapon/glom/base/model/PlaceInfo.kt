package io.jitrapon.glom.base.model

import android.os.Parcel
import android.os.Parcelable
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */
data class PlaceInfo(var name: String? = null,
                     var description: String? = null,
                     var avatar: String? = null,
                     var latitude: Double? = null,
                     var longitude: Double? = null,
                     var googlePlaceId: String? = null,
                     var placeId: String,
                     var status: Int = FAVORITED,
                     override var retrievedTime: Date? = Date(),
                     override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readValue(Double::class.java.classLoader) as? Double,
            parcel.readValue(Double::class.java.classLoader) as? Double,
            parcel.readString(),
            parcel.readString(),
            parcel.readInt(),
            parcel.readLong().let {
                if (it == -1L) null
                else Date(it)
            })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeString(avatar)
        parcel.writeValue(latitude)
        parcel.writeValue(longitude)
        parcel.writeString(googlePlaceId)
        parcel.writeString(placeId)
        parcel.writeInt(status)
        parcel.writeLong(retrievedTime?.time ?: -1L)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<PlaceInfo> {

        const val INTERESTED = 0
        const val PLANNED = 1
        const val VISITED = 2
        const val FAVORITED = 3

        override fun createFromParcel(parcel: Parcel): PlaceInfo {
            return PlaceInfo(parcel)
        }

        override fun newArray(size: Int): Array<PlaceInfo?> {
            return arrayOfNulls(size)
        }
    }
}