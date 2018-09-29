package io.jitrapon.glom.base.domain.user

import android.os.Parcel
import android.os.Parcelable
import io.jitrapon.glom.base.model.DataModel
import java.util.*

/**
 * Represents User model class
 *
 * @author Jitrapon Tiachunpun
 */
data class User(val userType: Int,
                val userId: String,
                var userName: String,
                var avatar: String?,
                override var retrievedTime: Date? = Date(),
                override val error: Throwable? = null) : DataModel {

    constructor(parcel: Parcel) : this(
            parcel.readInt(),
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString(),
            parcel.readLong().let {
                if (it == -1L) null
                else Date(it)
            })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(userType)
        parcel.writeString(userId)
        parcel.writeString(userName)
        parcel.writeString(avatar)
        parcel.writeLong(retrievedTime?.time ?: -1L)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<User> {

        val TYPE_USER = 1
        val TYPE_ENTITY = 2
        val TYPE_BOT = 3

        override fun createFromParcel(parcel: Parcel): User {
            return User(parcel)
        }

        override fun newArray(size: Int): Array<User?> {
            return arrayOfNulls(size)
        }
    }
}