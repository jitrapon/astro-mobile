package io.jitrapon.glom.base.domain.user.account

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import java.util.*

/**
 * DataModel class for a user account
 *
 * Created by Jitrapon
 */
data class AccountInfo(val userId: String,
                       val refreshToken: String?,
                       val idToken: String?,
                       override var retrievedTime: Date? = null,
                       override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(userId)
        parcel.writeString(refreshToken)
        parcel.writeString(idToken)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AccountInfo> {
        override fun createFromParcel(parcel: Parcel): AccountInfo {
            return AccountInfo(parcel)
        }

        override fun newArray(size: Int): Array<AccountInfo?> {
            return arrayOfNulls(size)
        }
    }
}