package io.jitrapon.glom.base.model

import androidx.annotation.PluralsRes

/**
 * Created by Jitrapon
 */
data class AndroidPluralString(@PluralsRes val resId: Int,
                               val quantity: Int,
                               val formatArgs: Array<out CharSequence>? = null,
                               override var status: UiModel.Status = UiModel.Status.SUCCESS): UiModel {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AndroidPluralString

        if (resId != other.resId) return false
        if (quantity != other.quantity) return false
        if (formatArgs != null) {
            if (other.formatArgs == null) return false
            if (!formatArgs.contentEquals(other.formatArgs)) return false
        } else if (other.formatArgs != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resId
        result = 31 * result + quantity
        result = 31 * result + (formatArgs?.contentHashCode() ?: 0)
        return result
    }
}