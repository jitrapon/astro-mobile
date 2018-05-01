package io.jitrapon.glom.base.domain.circle

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import io.jitrapon.glom.base.model.PlaceInfo
import io.jitrapon.glom.base.model.RepeatInfo
import java.util.*

/**
 * Represents a Circle data model
 *
 * @author Jitrapon Tiachunpun
 */
data class Circle(val circleId: String,
                  var name: String,
                  var avatar: String?,
                  var info: String?,
                  val interests: MutableList<String>,
                  var repeatInfo: RepeatInfo?,
                  val places: MutableList<PlaceInfo>,
                  override var retrievedTime: Date? = Date(),
                  override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.createStringArrayList(),
            parcel.readParcelable(RepeatInfo::class.java.classLoader),
            ArrayList<PlaceInfo>().apply {
                parcel.readTypedList(this, PlaceInfo)
            },
            parcel.readLong().let {
                if (it == -1L) null
                else Date(it)
            })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(circleId)
        parcel.writeString(name)
        parcel.writeString(avatar)
        parcel.writeString(info)
        parcel.writeParcelable(repeatInfo, flags)
        parcel.writeTypedList(places)
        parcel.writeLong(retrievedTime?.time ?: -1L)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Circle> {
        override fun createFromParcel(parcel: Parcel): Circle {
            return Circle(parcel)
        }

        override fun newArray(size: Int): Array<Circle?> {
            return arrayOfNulls(size)
        }
    }
}
