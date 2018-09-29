package io.jitrapon.glom.base.ui.widget.stickyheader

import android.os.Parcel
import android.os.Parcelable

/**
 * @author Jitrapon Tiachunpun
 */
class SavedState : Parcelable {

    lateinit var superState: Parcelable
    var pendingScrollPosition: Int = 0
    var pendingScrollOffset: Int = 0

    constructor()

    constructor(parcel: Parcel) {
        superState = parcel.readParcelable(SavedState::class.java.classLoader)!!
        pendingScrollPosition = parcel.readInt()
        pendingScrollOffset = parcel.readInt()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(superState, flags)
        dest.writeInt(pendingScrollPosition)
        dest.writeInt(pendingScrollOffset)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SavedState> {
        override fun createFromParcel(parcel: Parcel): SavedState {
            return SavedState(parcel)
        }

        override fun newArray(size: Int): Array<SavedState?> {
            return arrayOfNulls(size)
        }
    }
}