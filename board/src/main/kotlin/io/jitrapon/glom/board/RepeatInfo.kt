package io.jitrapon.glom.board

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import java.util.*

/**
 * @author Jitrapon Tiachunpun
 */
data class RepeatInfo(val occurenceId: Int?,
                      val isReschedule: Boolean?,
                      val unit: Int,
                      val interval: Long,
                      val until: Long,
                      val meta: List<Int>?,
                      override val retrievedTime: Date? = null,
                      override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readInt().let {
                if (it == -1) null else it
            },
            parcel.readInt().let {
                if (it == -1) null else it == 1
            },
            parcel.readInt(),
            parcel.readLong(),
            parcel.readLong(),
            ArrayList<Int>().let {
                parcel.readList(it, null)
                if (it.isEmpty()) null else it
            })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(occurenceId ?: -1)
        parcel.writeInt(if (isReschedule == null) -1 else {
            if (isReschedule) 1 else 0
        })
        parcel.writeInt(unit)
        parcel.writeLong(interval)
        parcel.writeLong(until)
        parcel.writeList(meta)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RepeatInfo> {
        override fun createFromParcel(parcel: Parcel): RepeatInfo = RepeatInfo(parcel)

        override fun newArray(size: Int): Array<RepeatInfo?> = arrayOfNulls(size)
    }
}
