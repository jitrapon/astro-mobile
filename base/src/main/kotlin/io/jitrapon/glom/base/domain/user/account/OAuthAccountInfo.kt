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
data class OAuthAccountInfo(val userId: String,
                            val refreshToken: String,
                            val idToken: String,
                            val expiresIn: Long,
                            val email: String?,
                            val providerId: String,
                            val fullName: String?,
                            val displayName: String?,
                            val photoUrl: String?,
                            override var retrievedTime: Date? = null,
                            override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readLong(),
            parcel.readString(),
            parcel.readString()!!,
            parcel.readString(),
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(userId)
        parcel.writeString(refreshToken)
        parcel.writeString(idToken)
        parcel.writeLong(expiresIn)
        parcel.writeString(email)
        parcel.writeString(providerId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<OAuthAccountInfo> {
        override fun createFromParcel(parcel: Parcel): OAuthAccountInfo {
            return OAuthAccountInfo(parcel)
        }

        override fun newArray(size: Int): Array<OAuthAccountInfo?> {
            return arrayOfNulls(size)
        }
    }
}
